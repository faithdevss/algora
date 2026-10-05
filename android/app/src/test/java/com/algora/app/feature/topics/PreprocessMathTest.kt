package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B8's guard for feature scaling, which the z-score and min-max storyboards read: the rule is scored
 * against a k-NN model instead of asserted, and min-max and z-score turn out to differ only under
 * contamination.
 */
class PreprocessMathTest {

    // ── Scaling ──────────────────────────────────────────────────────────────

    @Test
    fun `unscaled, the k-NN distance is one feature`() {
        val shares = FeatureScalingLab.distanceShares.toMap()
        assertEquals("income supplies essentially all of it", 1.0, shares.getValue("raw")[1], 0.001)
        assertTrue("age supplies essentially none", shares.getValue("raw")[0] < 0.001)
    }

    @Test
    fun `scaling makes both features count`() {
        val shares = FeatureScalingLab.distanceShares.toMap()
        listOf("min-max", "z-score").forEach { name ->
            assertTrue(
                "$name should leave both columns comparable",
                shares.getValue(name).all { it in 0.35..0.65 },
            )
        }
    }

    @Test
    fun `and it moves the accuracy of a model that was not retrained`() {
        val results = FeatureScalingLab.results.associateBy { it.name }
        assertEquals(0.738, results.getValue("raw").accuracy, 0.03)
        assertEquals(0.900, results.getValue("min-max").accuracy, 0.03)
        assertEquals(0.888, results.getValue("z-score").accuracy, 0.03)
        assertTrue(
            "if scaling stops helping here, both scaler topics lose their argument",
            results.getValue("min-max").accuracy > results.getValue("raw").accuracy + 0.1,
        )
    }

    @Test
    fun `min-max produces a unit range and z-score does not`() {
        val results = FeatureScalingLab.results.associateBy { it.name }
        results.getValue("min-max").ranges.forEach { assertEquals(1.0, it, 1e-9) }
        assertTrue("z-score ranges depend on where the extremes sit", results.getValue("z-score").ranges.all { it > 2.5 })
    }

    @Test
    fun `the two scalers separate under contamination, not on clean data`() {
        // The measured reason to prefer one over the other, since on clean data they are a point
        // apart. One extreme value in the fitting set collapses min-max and merely dents z-score.
        val effect = FeatureScalingLab.outlierEffect
        assertEquals(0.026, effect.minMaxSpan, 0.01)
        assertEquals(0.289, effect.zScoreSpan, 0.05)
        assertTrue("min-max must be the one that collapses", effect.zScoreSpan > effect.minMaxSpan * 5)
    }
}
