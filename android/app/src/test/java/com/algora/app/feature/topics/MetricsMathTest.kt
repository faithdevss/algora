package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B9's guard, pinning the numbers `MetricsMath.kt`'s header comment promises. Every classification
 * figure below comes from the same fitted logistic model (`ScoredLab`), every regression figure from
 * the same pair of fits (`RegressionMetricsLab`), so a change here is a change to what every one of
 * the seventeen content files quotes, not an isolated fact.
 */
class MetricsMathTest {

    // ── Confusion matrix / accuracy / kappa ─────────────────────────────────────

    @Test
    fun `the model at t=0_5 is the lab's headline matrix`() {
        val cells = ScoredLab.cellsAt(0.5)
        assertEquals(35, cells.tp); assertEquals(0, cells.fp)
        assertEquals(53, cells.fn); assertEquals(912, cells.tn)
        assertEquals(0.947, cells.accuracy, 0.001)
    }

    @Test
    fun `at t=0_9 accuracy equals the majority baseline and kappa knows it`() {
        val cells = ScoredLab.cellsAt(0.9)
        assertEquals(0, cells.tp); assertEquals(88, cells.fn)
        assertEquals(ScoredLab.majorityAccuracy, cells.accuracy, 1e-9)
        assertEquals(0.912, cells.accuracy, 0.001)
        assertEquals("same predictions, zero agreement beyond chance", 0.0, cells.kappa, 1e-9)
    }

    @Test
    fun `at t=0_5 kappa discounts accuracy substantially, and MCC agrees it's real`() {
        val cells = ScoredLab.cellsAt(0.5)
        assertEquals(0.546, cells.kappa, 0.001)
        assertEquals(0.613, cells.matthews, 0.001)
        assertTrue("kappa must sit well below the raw accuracy", cells.kappa < cells.accuracy - 0.3)
    }

    @Test
    fun `F1 at the default threshold is not the best F1 available`() {
        val default = ScoredLab.cellsAt(0.5).f1
        assertEquals(0.569, default, 0.001)
        assertEquals(0.34, ScoredLab.bestF1Threshold, 0.001)
        assertEquals(0.776, ScoredLab.bestF1, 0.001)
        assertTrue("a free 20-point gain from moving the threshold, not retraining", ScoredLab.bestF1 > default + 0.15)
    }

    // ── ROC / AUC / log loss ─────────────────────────────────────────────────────

    @Test
    fun `the two AUC definitions agree`() {
        assertEquals(0.9692, ScoredLab.auc, 0.001)
        assertEquals(
            "trapezoid area and pairwise-win probability are the same statistic",
            ScoredLab.auc, ScoredLab.aucByRanking, 0.001,
        )
    }

    @Test
    fun `a monotone recalibration leaves AUC unchanged`() {
        assertEquals(ScoredLab.auc, ScoredLab.overconfidentAuc, 0.001)
    }

    @Test
    fun `but it does not leave log loss or Brier unchanged`() {
        val calibratedLogLoss = ScoredLab.logLoss()
        val overconfidentLogLoss = ScoredLab.logLoss(ScoredLab.overconfident)
        assertEquals(0.1750, calibratedLogLoss, 0.001)
        assertEquals(0.2876, overconfidentLogLoss, 0.001)
        assertTrue("log loss must worsen under the same distortion AUC could not see", overconfidentLogLoss > calibratedLogLoss * 1.5)

        val calibratedBrier = ScoredLab.brier()
        val overconfidentBrier = ScoredLab.brier(ScoredLab.overconfident)
        assertEquals(0.0437, calibratedBrier, 0.001)
        assertEquals(0.0556, overconfidentBrier, 0.001)

        val logLossRelativeJump = overconfidentLogLoss / calibratedLogLoss - 1
        val brierRelativeJump = overconfidentBrier / calibratedBrier - 1
        assertTrue("log loss reacts harder than its squared-error cousin", logLossRelativeJump > brierRelativeJump)
    }

    @Test
    fun `one confidently wrong prediction costs roughly 13x the mean`() {
        val ce = ScoredLab.confidentError
        assertEquals(2.214, ce.worstContribution, 0.01)
        assertEquals(0.1750, ce.meanContribution, 0.001)
        val ratio = ce.worstContribution / ce.meanContribution
        assertTrue("the worst single case is well over 10x an average one", ratio in 10.0..15.0)
    }

    // ── Regression: MSE / RMSE / MAE / R² / adjusted R² ──────────────────────────

    @Test
    fun `the least-squares fit minimises MSE, not MAE`() {
        val ls = RegressionMetricsLab.squaredLossFit
        val lad = RegressionMetricsLab.absoluteLossFit
        assertEquals(18.482, RegressionMetricsLab.mse(ls), 0.01)
        assertEquals(21.765, RegressionMetricsLab.mse(lad), 0.01)
        assertTrue("least squares must win under its own metric", RegressionMetricsLab.mse(ls) < RegressionMetricsLab.mse(lad))
    }

    @Test
    fun `the least-absolute-deviations fit minimises MAE instead, and wins there`() {
        val ls = RegressionMetricsLab.squaredLossFit
        val lad = RegressionMetricsLab.absoluteLossFit
        assertEquals(2.601, RegressionMetricsLab.mae(ls), 0.01)
        assertEquals(1.894, RegressionMetricsLab.mae(lad), 0.01)
        assertTrue("neither fit dominates the other", RegressionMetricsLab.mae(lad) < RegressionMetricsLab.mae(ls))
    }

    @Test
    fun `RMSE is a monotonic rescaling of MSE and never reorders the fits`() {
        val fits = listOf(RegressionMetricsLab.squaredLossFit, RegressionMetricsLab.absoluteLossFit, RegressionMetricsLab.worseThanMean)
        val byMse = fits.sortedBy { RegressionMetricsLab.mse(it) }
        val byRmse = fits.sortedBy { RegressionMetricsLab.rmse(it) }
        assertEquals(byMse.map { it.name }, byRmse.map { it.name })
        assertEquals(4.299, RegressionMetricsLab.rmse(RegressionMetricsLab.squaredLossFit), 0.01)
    }

    @Test
    fun `the least-squares slope is pulled toward the contamination, LAD's is not`() {
        assertEquals(1.4, RegressionMetricsLab.trueSlope, 1e-9)
        assertTrue("least squares overshoots the true slope", RegressionMetricsLab.squaredLossFit.slope > 1.6)
        assertTrue("LAD stays close to it", kotlin.math.abs(RegressionMetricsLab.absoluteLossFit.slope - 1.4) < 0.1)
    }

    @Test
    fun `four contaminated points carry the large majority of the squared error`() {
        assertEquals(0.091, RegressionMetricsLab.outlierCountShare, 0.005)
        assertEquals(0.812, RegressionMetricsLab.outlierErrorShare, 0.02)
        assertTrue("9% of the points, over 80% of the error", RegressionMetricsLab.outlierErrorShare > RegressionMetricsLab.outlierCountShare * 5)
    }

    @Test
    fun `R2 has a floor at the mean baseline and can go negative below it`() {
        assertEquals(0.611, RegressionMetricsLab.rSquared(RegressionMetricsLab.squaredLossFit), 0.01)
        assertTrue("a deliberately bad model beats nothing but negative territory", RegressionMetricsLab.rSquared(RegressionMetricsLab.worseThanMean) < 0.0)
    }

    @Test
    fun `R2 rises at every noise column while adjusted R2 nets a fall`() {
        val steps = RegressionMetricsLab.noiseColumns
        for (i in 1 until steps.size) {
            assertTrue("R2 must be non-decreasing at step $i", steps[i].rSquared >= steps[i - 1].rSquared - 1e-9)
        }
        assertTrue(
            "adjusted R2 nets a real drop over the run despite not being strictly monotone",
            steps.last().adjusted < steps.first().adjusted - 0.03,
        )
    }

    // ── Split impurity ────────────────────────────────────────────────────────

    @Test
    fun `a balanced parent maxes both gini and entropy`() {
        assertEquals(0.5, ImpurityLab.gini(200, 200), 1e-9)
        assertEquals(1.0, ImpurityLab.entropy(200, 200), 1e-9)
    }

    @Test
    fun `a perfect split maxes gini, entropy and error gain alike`() {
        val perfect = ImpurityLab.scores.first { it.split.name.startsWith("B:") }
        assertEquals(0.5, perfect.giniGain, 1e-9)
        assertEquals(1.0, perfect.entropyGain, 1e-9)
        assertEquals(0.5, perfect.errorGain, 1e-9)
    }

    @Test
    fun `misclassification rate ties two splits that gini and entropy correctly separate`() {
        val (a, c) = ImpurityLab.tiedForError
        assertEquals(a.errorGain, c.errorGain, 1e-9)
        assertTrue("gini must break the tie the error rate cannot see", kotlin.math.abs(a.giniGain - c.giniGain) > 0.01)
    }

    // ── Margin losses ────────────────────────────────────────────────────────

    @Test
    fun `hinge loss goes exactly flat past the margin, logistic never does`() {
        assertEquals(110, MarginLab.active.hingeActive)
        assertEquals(1000, MarginLab.active.logisticActive)
        assertEquals(0.0, MarginLab.hingeGradient(5.0), 1e-9)
        assertTrue("logistic still has a nonzero, shrinking gradient", MarginLab.logisticGradientAt(10.0) > 0.0)
        assertTrue(MarginLab.logisticGradientAt(10.0) < 1e-3)
    }

    // ── Clustering indices ─────────────────────────────────────────────────────

    @Test
    fun `silhouette and Davies-Bouldin both find the true k on well-separated blobs`() {
        val sweep = ClusterMetricsLab.blobSweep
        assertEquals(3, ClusterMetricsLab.bestBySilhouette(sweep))
        assertEquals(3, ClusterMetricsLab.bestByDaviesBouldin(sweep))
        assertEquals(0.8452, sweep.first { it.k == 3 }.silhouette, 0.01)
        assertEquals(0.2214, sweep.first { it.k == 3 }.daviesBouldin, 0.01)
    }

    @Test
    fun `both indices agree on the wrong k for concentric rings`() {
        val sweep = ClusterMetricsLab.ringSweep
        assertEquals("the true structure is 2 rings", 2, ClusterMetricsLab.RING_TRUTH)
        assertTrue("silhouette does not pick the true k", ClusterMetricsLab.bestBySilhouette(sweep) != ClusterMetricsLab.RING_TRUTH)
        assertEquals(
            "the two indices fail on the same wrong answer, not independently",
            ClusterMetricsLab.bestBySilhouette(sweep), ClusterMetricsLab.bestByDaviesBouldin(sweep),
        )
    }
}
