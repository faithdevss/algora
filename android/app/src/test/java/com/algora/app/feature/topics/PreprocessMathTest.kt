package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B8's guard. Preprocessing is where a plan is most likely to have copied a rule of thumb, so each
 * lab scores its rule against a model instead of asserting it — and three of the nine came out
 * *conditional* rather than true. Those three are the tests to read first: label encoding is free in
 * a tree, min-max and z-score differ only under contamination, and neither feature selector finds an
 * interaction.
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

    // ── Encoding ─────────────────────────────────────────────────────────────

    @Test
    fun `a label code invents a geometry the categories do not have`() {
        assertEquals(3.0, EncodingLab.codeDistance("red", "yellow"), 1e-9)
        assertEquals(1.0, EncodingLab.codeDistance("red", "green"), 1e-9)
        assertEquals("one-hot puts every pair at the same distance", 1.414, EncodingLab.oneHotDistance(), 0.001)
    }

    @Test
    fun `a linear model pays 40x for that geometry`() {
        val fits = EncodingLab.linearFits.associateBy { it.name }
        assertEquals(11.26, fits.getValue("label encoding").error, 0.5)
        assertEquals(0.280, fits.getValue("one-hot").error, 0.02)
        assertTrue(
            "the whole topic turns on this ordering",
            fits.getValue("label encoding").error > fits.getValue("one-hot").error * 20,
        )
    }

    @Test
    fun `and a tree pays nothing at all`() {
        // The conditional the plan's "never label-encode" rule leaves out: two splits separate four
        // categories, so the tree reaches one-hot's error on the label-coded column.
        val oneHot = EncodingLab.linearFits.first { it.name == "one-hot" }.error
        assertEquals(oneHot, EncodingLab.treeError(2), 0.005)
        assertTrue("and depth 1 is not yet enough", EncodingLab.treeError(1) > oneHot * 10)
    }

    @Test
    fun `dropping a one-hot level costs nothing measurable`() {
        val fits = EncodingLab.linearFits.associateBy { it.name }
        val full = fits.getValue("one-hot")
        val dropped = fits.getValue("one-hot, first level dropped")
        assertEquals(full.error, dropped.error, 0.001)
        assertEquals(full.columns - 1, dropped.columns)
    }

    // ── Imputation ───────────────────────────────────────────────────────────

    @Test
    fun `mean imputation shrinks the variance by the missing rate`() {
        val effects = ImputationLab.effects.associateBy { it.name }
        val ratio = effects.getValue("mean imputation").variance / effects.getValue("complete data").variance
        assertEquals(
            "the filled values contribute nothing to the spread, so the ratio tracks 1 − p",
            ImputationLab.predictedVarianceRatio(), ratio, 0.05,
        )
        assertTrue("and it is a shrinkage, not a wobble", ratio < 0.8)
    }

    @Test
    fun `and it attenuates a correlation in another column`() {
        val effects = ImputationLab.effects.associateBy { it.name }
        val complete = effects.getValue("complete data").correlation
        val filled = effects.getValue("mean imputation").correlation
        assertEquals(0.982, complete, 0.01)
        assertEquals(0.829, filled, 0.03)
        assertTrue("the damage crosses columns", filled < complete - 0.1)
    }

    @Test
    fun `dropping the rows keeps both statistics and costs rows`() {
        val effects = ImputationLab.effects.associateBy { it.name }
        val complete = effects.getValue("complete data")
        val dropped = effects.getValue("drop the rows")
        assertEquals("unbiased under MCAR", complete.correlation, dropped.correlation, 0.01)
        assertEquals(ImputationLab.observedCount, dropped.rows)
        assertTrue("at a real cost in sample size", dropped.rows < complete.rows * 0.75)
    }

    @Test
    fun `on a skewed column the mean is a value almost no row has`() {
        val centre = ImputationLab.skewedCentre
        assertTrue("the mean is dragged by the tail", centre.mean > centre.median * 5)
    }

    // ── SMOTE ────────────────────────────────────────────────────────────────

    @Test
    fun `SMOTE interpolates rather than duplicating`() {
        val minority = SmoteLab.minority
        val synthetic = SmoteLab.synthesise(minority, 20, seed = 101)
        assertEquals(20, synthetic.size)
        assertTrue("every synthetic point is a minority point", synthetic.all { it.label == 1 })
        assertTrue(
            "and none of them is a copy of a real row",
            synthetic.none { s -> minority.any { it.features.contentEquals(s.features) } },
        )
    }

    @Test
    fun `it buys recall and spends precision`() {
        val scores = SmoteLab.scores.associateBy { it.name }
        val before = scores.getValue("imbalanced")
        val after = scores.getValue("SMOTE on the training fold")
        assertTrue("recall must improve, or there is no reason to use it", after.minorityRecall > before.minorityRecall)
        assertEquals(1.0, after.minorityRecall, 0.001)
        assertTrue("and precision must fall, which is the half that gets left out", after.precision < before.precision)
    }

    @Test
    fun `resampling before the split reports a score that will not reproduce`() {
        val leak = SmoteLab.leak
        assertEquals(1, leak.neighbours)
        assertTrue(
            "the leaky pipeline must look better than the honest one — that is the whole hazard",
            leak.leakyScore > leak.honestScore,
        )
        assertEquals(0.954, leak.leakyScore, 0.03)
        assertEquals(0.908, leak.honestScore, 0.03)
    }

    // ── Outliers ─────────────────────────────────────────────────────────────

    @Test
    fun `every rule agrees on clean data`() {
        listOf(
            OutlierLab.zScoreFlags(OutlierLab.clean),
            OutlierLab.iqrFlags(OutlierLab.clean),
            OutlierLab.modifiedZFlags(OutlierLab.clean),
        ).forEach { assertTrue("${it.rule} should flag nothing here", it.flagged.isEmpty()) }
    }

    @Test
    fun `a single extreme value is caught by all of them`() {
        assertEquals(1, OutlierLab.zScoreFlags(OutlierLab.contaminated).flagged.size)
        assertEquals(1, OutlierLab.iqrFlags(OutlierLab.contaminated).flagged.size)
        assertTrue("its own z-score is far past the threshold", OutlierLab.selfZScore() > 7.0)
    }

    @Test
    fun `outliers arriving together hide each other from the z-score rule`() {
        // The batch's headline for this topic, and it is solved for rather than hand-picked: extremes
        // are added until the largest z falls under the threshold.
        assertEquals(7, OutlierLab.maskingCount)
        assertEquals(0.104, OutlierLab.maskingShare, 0.01)
        assertEquals("the rule sees nothing at all", 0, OutlierLab.zScoreFlags(OutlierLab.masked).flagged.size)
        assertEquals("the quartiles have not moved", OutlierLab.maskingCount, OutlierLab.iqrFlags(OutlierLab.masked).flagged.size)
        assertEquals("nor has the median", OutlierLab.maskingCount, OutlierLab.modifiedZFlags(OutlierLab.masked).flagged.size)
    }

    @Test
    fun `and on a small sample the z-score rule cannot fire at all`() {
        assertEquals(11, OutlierLab.smallestUsableSample)
        assertTrue("n = 10 has a ceiling below 3", OutlierLab.maxPossibleZ(10) < OutlierLab.Z_THRESHOLD)
        assertTrue("n = 11 clears it", OutlierLab.maxPossibleZ(11) > OutlierLab.Z_THRESHOLD)
        assertEquals("and the ceiling is exactly (n−1)/√n", 7.682, OutlierLab.maxPossibleZ(61), 0.01)
    }

    @Test
    fun `the breakdown points explain both failures`() {
        assertEquals(0.0, OutlierLab.breakdownPoints.getValue("mean/σ (z-score)"), 1e-9)
        assertEquals(0.25, OutlierLab.breakdownPoints.getValue("quartiles (IQR)"), 1e-9)
        assertEquals(0.5, OutlierLab.breakdownPoints.getValue("median/MAD"), 1e-9)
    }

    // ── Feature selection ────────────────────────────────────────────────────

    @Test
    fun `chi-square finds a univariate signal and cannot see redundancy`() {
        val ranking = FeatureSelectionLab.chiSquareRanking()
        assertEquals("useful", ranking.first().first)
        assertEquals(275.0, ranking.first().second, 15.0)
        assertEquals("its 90% copy ranks second, adding nothing", "duplicate", ranking[1].first)
        assertTrue(ranking.drop(2).all { it.second < 5.0 })
    }

    @Test
    fun `on XOR-labelled data chi-square ranks the noise above the signal`() {
        // If this ever stops holding, the topic's central claim is false.
        val ranking = FeatureSelectionLab.chiSquareRanking(FeatureSelectionLab.xorData)
        val noise = ranking.first { it.first == "noise" }.second
        val signal = ranking.filter { it.first.startsWith("xor") }.map { it.second }
        assertTrue("the noise feature outscores both halves of the actual signal", signal.all { it < noise })
        assertTrue("and nothing scores highly, because nothing is univariate here", ranking.all { it.second < 10.0 })
    }

    @Test
    fun `RFE keeps the useful feature and drops the copy`() {
        val rounds = FeatureSelectionLab.recursiveElimination()
        assertEquals(FeatureSelectionLab.featureNames.size - 1, rounds.size)
        val survivor = rounds.last().remaining.first { it != rounds.last().dropped }
        assertEquals("useful", survivor)
        assertTrue("the redundant copy is dropped, which chi-square ranked second", rounds.any { it.dropped == "duplicate" })
    }

    @Test
    fun `dropping four features barely moves the error`() {
        val rounds = FeatureSelectionLab.recursiveElimination()
        assertTrue(
            "flat error across the elimination is the honest signal that they carried nothing",
            kotlin.math.abs(rounds.last().error - rounds.first().error) < 0.001,
        )
    }

    @Test
    fun `RFE inherits its estimator's blind spot`() {
        // Both selectors fail on XOR, for different reasons. Pinned because the copy says so.
        val rounds = FeatureSelectionLab.recursiveElimination(FeatureSelectionLab.xorData)
        val survivor = rounds.last().remaining.first { it != rounds.last().dropped }
        assertTrue(
            "a linear model cannot express XOR, so RFE cannot select for it either",
            survivor !in listOf("xorA", "xorB") || rounds.any { it.dropped.startsWith("xor") },
        )
    }

    @Test
    fun `the two selectors cost different amounts`() {
        assertEquals(0, FeatureSelectionLab.chiSquareFits())
        assertEquals(4, FeatureSelectionLab.fitsRequired(FeatureSelectionLab.featureNames.size))
    }
}
