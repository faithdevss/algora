package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C8's guard, the phase's last. Momentum/AdaGrad/RMSprop/Adam/AdamW/LrScheduler run real update
 * rules; CrossEntropy and KlDivergence check identities to floating-point tolerance, because the
 * claim in the copy is that they are exact, not approximate.
 */
class OptimizerMathTest {

    // ── Momentum ────────────────────────────────────────────────────────────

    @Test
    fun `beta=0_9 wins the sweep by more than an order of magnitude`() {
        val runs = MomentumLab.runs
        val loss09 = runs.getValue(0.9).finalLoss
        assertEquals(0.00699, loss09, 0.0005)
        assertTrue("beta=0.9 beats beta=0.5 by more than 10x", runs.getValue(0.5).finalLoss / loss09 > 10.0)
        assertTrue("beta=0.9 beats beta=0.99 by more than 10x", runs.getValue(0.99).finalLoss / loss09 > 10.0)
    }

    @Test
    fun `beta=0_99 overshoots harder than beta=0_9 and ends up worse`() {
        val runs = MomentumLab.runs
        val r9 = runs.getValue(0.9)
        val r99 = runs.getValue(0.99)
        assertTrue("0.99's first swing back overshoots further than 0.9's", r99.maxAbsW1AfterStep5 > r9.maxAbsW1AfterStep5)
        assertTrue("and ends with a worse final loss despite -- because of -- more momentum", r99.finalLoss > r9.finalLoss)
    }

    // ── AdaGrad ─────────────────────────────────────────────────────────────

    @Test
    fun `the sparse feature keeps a higher effective rate at t=200`() {
        val at200 = AdaGradLab.at(200)
        assertEquals(200.0, at200.gAccumDense, 1e-9)
        assertEquals(80.0, at200.gAccumSparse, 1e-9)
        assertEquals(1.58, at200.effRateSparse / at200.effRateDense, 0.02)
    }

    @Test
    fun `the dense rate decays exactly as lr over sqrt t`() {
        val at200 = AdaGradLab.at(200)
        val at2000 = AdaGradLab.at(2000)
        assertEquals(AdaGradLab.LR / Math.sqrt(200.0), at200.effRateDense, 1e-6)
        assertEquals(AdaGradLab.LR / Math.sqrt(2000.0), at2000.effRateDense, 1e-6)
        assertTrue("the rate never stops shrinking", at2000.effRateDense < at200.effRateDense)
    }

    // ── RMSprop ─────────────────────────────────────────────────────────────

    @Test
    fun `RMSprop's dense rate stays flat where AdaGrad's keeps shrinking`() {
        val rms200 = RmsPropLab.at(200)
        val rms2000 = RmsPropLab.at(2000)
        assertEquals(rms200.effRateDense, rms2000.effRateDense, 0.001)
        assertEquals(0.5, rms2000.effRateDense, 0.01)
        val ada2000 = AdaGradLab.at(2000)
        assertTrue("RMSprop's flat rate is far above AdaGrad's still-shrinking one", rms2000.effRateDense / ada2000.effRateDense > 40.0)
    }

    @Test
    fun `RMSprop keeps less of the sparse-feature boost than AdaGrad does`() {
        val rms200 = RmsPropLab.at(200)
        val ada200 = AdaGradLab.at(200)
        val rmsBoost = rms200.effRateSparse / rms200.effRateDense
        val adaBoost = ada200.effRateSparse / ada200.effRateDense
        assertTrue("the EMA forgets between firings, so its boost is smaller", rmsBoost < adaBoost)
        assertEquals(1.28, rmsBoost, 0.02)
        assertEquals(1.58, adaBoost, 0.02)
    }

    // ── Adam ────────────────────────────────────────────────────────────────

    @Test
    fun `the bias-corrected step ratio is exactly 1 at every step on a constant gradient`() {
        for (t in listOf(1, 2, 10, 50, 100)) {
            assertEquals("corrected ratio at t=$t", 1.0, AdamLab.at(t).correctedRatio, 1e-9)
        }
    }

    @Test
    fun `the uncorrected ratio wanders well away from 1 before settling`() {
        val at1 = AdamLab.at(1)
        val peak = AdamLab.history.maxBy { it.uncorrectedRatio }
        assertEquals(3.162, at1.uncorrectedRatio, 0.01)
        assertEquals(12, peak.t)
        assertEquals(6.569, peak.uncorrectedRatio, 0.01)
        assertTrue("still measurably wrong by step 100", AdamLab.at(100).uncorrectedRatio > 2.0)
    }

    // ── AdamW ───────────────────────────────────────────────────────────────

    @Test
    fun `histories with 10x different gradient magnitude land 100x apart in v`() {
        assertEquals(100.0, AdamWLab.vLargeHistory / AdamWLab.vSmallHistory, 0.5)
    }

    @Test
    fun `L2-in-Adam decays the small-v parameter 10x faster than the large-v one`() {
        val large = AdamWLab.resultLargeV
        val small = AdamWLab.resultSmallV
        assertEquals(10.0, small.stepL2 / large.stepL2, 0.1)
    }

    @Test
    fun `AdamW decays both parameters by exactly the same fraction`() {
        val large = AdamWLab.resultLargeV
        val small = AdamWLab.resultSmallV
        assertEquals(1.0, small.stepDecoupled / large.stepDecoupled, 1e-9)
        assertEquals(AdamWLab.LR * AdamWLab.WD, large.stepDecoupled, 1e-9)
    }

    // ── LR Schedulers ───────────────────────────────────────────────────────

    @Test
    fun `on the clean bowl, step decay and constant beat cosine and warmup`() {
        fun finalLoss(path: List<DoubleArray>) = LrSchedulerLab.loss(path.last())
        val constant = finalLoss(LrSchedulerLab.constantPath)
        val stepDecay = finalLoss(LrSchedulerLab.stepDecayPath)
        val cosine = finalLoss(LrSchedulerLab.cosinePath)
        val warmup = finalLoss(LrSchedulerLab.warmupCosinePath)
        assertTrue("step decay beats constant here", stepDecay < constant)
        assertTrue("cosine is worse than constant with no noise to justify decay", cosine > constant)
        assertTrue("warmup+cosine is worse still", warmup > cosine)
    }

    @Test
    fun `under a persistent disturbance, decay shrinks the noise floor close to the lr-squared law`() {
        val constAvg = LrSchedulerLab.constantTailAvg
        val cosAvg = LrSchedulerLab.cosineTailAvg
        assertTrue("decay wins once there's a floor to shrink", cosAvg < constAvg)
        val ratio = constAvg / cosAvg
        val predicted = Math.pow(LrSchedulerLab.PERTURBED_LR_HIGH / LrSchedulerLab.PERTURBED_LR_LOW, 2.0)
        assertEquals(6.25, predicted, 1e-9)
        assertEquals(predicted, ratio, 1.0)
    }

    // ── Cross-Entropy Loss ──────────────────────────────────────────────────

    @Test
    fun `the direct p-y gradient matches the Jacobian gradient exactly`() {
        assertEquals(0.0, CrossEntropyLab.confidentCorrect.maxGradDiff, 1e-12)
        assertEquals(0.0, CrossEntropyLab.misclassified.maxGradDiff, 1e-12)
    }

    @Test
    fun `a misclassified example costs several times more than a confidently-correct one`() {
        val correct = CrossEntropyLab.confidentCorrect
        val wrong = CrossEntropyLab.misclassified
        assertEquals(0.417, correct.loss, 0.001)
        assertEquals(2.317, wrong.loss, 0.001)
        assertEquals(5.56, wrong.loss / correct.loss, 0.01)
    }

    // ── KL Divergence ───────────────────────────────────────────────────────

    @Test
    fun `KL divergence is asymmetric on the example distributions`() {
        assertEquals(0.2442, KlDivergenceLab.klPQ, 0.001)
        assertEquals(0.3112, KlDivergenceLab.klQP, 0.001)
        assertTrue("swapping the argument order changes the value", KlDivergenceLab.klPQ != KlDivergenceLab.klQP)
    }

    @Test
    fun `cross-entropy equals entropy plus forward KL, exactly`() {
        val sum = KlDivergenceLab.entropyP + KlDivergenceLab.klPQ
        assertEquals(KlDivergenceLab.crossEntropyPQ, sum, 1e-9)
    }

    @Test
    fun `forward KL fits a wide mode-covering Gaussian, reverse KL a narrow mode-seeking one`() {
        val forward = KlDivergenceLab.forwardFit
        val reverse = KlDivergenceLab.reverseFit
        assertTrue("forward fit is wider than reverse", forward.sigma > reverse.sigma)
        assertEquals(0.0, forward.mu, 0.6)
        assertEquals(-2.5, reverse.mu, 0.15)
        assertEquals(0.8, reverse.sigma, 0.15)
    }
}
