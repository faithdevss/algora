package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C9's specialized/graph guard. Siamese, GCN oversmoothing and the KAN/MLP comparison are
 * experiments, pinned on value with room for run-to-run noise. GAT-vs-GCN's feature-sensitivity
 * and the Euler/RK4 convergence orders are closer to identities -- the first is an exact
 * before/after equality on GCN's side, the second is a classical numerical-analysis fact that holds
 * for any smooth system, so both are pinned tighter.
 */
class SpecializedMathTest {

    // ── Siamese Networks ─────────────────────────────────────────────────────

    @Test
    fun `raw nearest neighbor is dragged around by the nuisance dimension`() {
        assertEquals(0.646, SiameseLab.result.rawAccuracy, 0.06)
    }

    @Test
    fun `the learned embedding generalizes to a class it never trained on`() {
        val r = SiameseLab.result
        assertEquals(1.0, r.embeddedAccuracy, 0.03)
        assertTrue(
            "class 3 was never in a training pair, and the embedding still wins by a wide margin",
            r.embeddedAccuracy - r.rawAccuracy > 0.25,
        )
    }

    // ── GCN ───────────────────────────────────────────────────────────────────

    @Test
    fun `one layer of aggregation already blurs the two triangles`() {
        val trace = GcnLab.propagationTrace
        assertEquals(10.67, GcnLab.separation(trace[0]), 0.5)
        assertTrue("one layer in, separation has dropped sharply", GcnLab.separation(trace[1]) < GcnLab.separation(trace[0]) * 0.8)
    }

    @Test
    fun `oversmoothing converges to exactly two-thirds, not to one`() {
        // The fixed point of repeated normalized-adjacency propagation is proportional to
        // sqrt(degree) alone. Nodes 0,1,4,5 share degree 3; nodes 2,3 (the bridge) share degree 4 --
        // so full convergence sorts nodes by degree, not by triangle, and this graph's degree
        // pattern happens to make bridge-to-bridge distance collapse to zero across groups while
        // same-triangle, different-degree distance does not. 2/3 is the exact ratio that leaves.
        val trace = GcnLab.layers(GcnLab.initialFeatures(), depth = 400)
        assertEquals(2.0 / 3.0, GcnLab.separation(trace[400]), 0.002)
        assertEquals(2.0 / 3.0, GcnLab.separation(trace[200]), 0.002)
    }

    @Test
    fun `and the two bridge nodes -- one from each triangle -- become the most similar pair in the whole graph`() {
        val converged = GcnLab.layers(GcnLab.initialFeatures(), depth = 400)[400]
        val bridgeDistance = GcnLab.distance(converged[2], converged[3])
        val sameTriangleDistance = GcnLab.distance(converged[0], converged[2])
        assertTrue(
            "node 2 and node 3 are in different triangles and converge to (almost) the same embedding",
            bridgeDistance < sameTriangleDistance * 0.05,
        )
    }

    // ── GAT ───────────────────────────────────────────────────────────────────

    @Test
    fun `gcn gives the bridge node's three neighbors nearly equal, feature-blind weight`() {
        val w = GatLab.baseGcn
        assertEquals(0.289, w.getValue(0), 0.01)
        assertEquals(0.289, w.getValue(1), 0.01)
        assertEquals(0.25, w.getValue(3), 0.01)
    }

    @Test
    fun `gat concentrates attention on the feature-similar neighbors instead`() {
        val w = GatLab.baseAttention
        assertEquals(0.613, w.getValue(0), 0.02)
        assertEquals(0.304, w.getValue(1), 0.02)
        assertEquals(0.083, w.getValue(3), 0.02)
        assertTrue("the two feature-similar neighbors take most of the mass", w.getValue(0) + w.getValue(1) > 0.85)
    }

    @Test
    fun `perturbing one neighbor's features moves gat's weight and leaves gcn's exactly unchanged`() {
        val before = GatLab.baseAttention.getValue(3)
        val after = GatLab.perturbedAttention.getValue(3)
        assertTrue("node 3's weight roughly quadruples once its features match the center", after / before > 3.0)

        val gcnBefore = GatLab.baseGcn.getValue(3)
        val gcnAfter = GatLab.gcnWeights().getValue(3)
        assertEquals("degree-normalization has no feature input to react to", gcnBefore, gcnAfter, 1e-12)
    }

    // ── Capsule Networks ──────────────────────────────────────────────────────

    @Test
    fun `three routing iterations already shift weight toward the two agreeing votes`() {
        val rounds = CapsuleLab.rounds
        assertEquals(listOf(0.5, 0.5, 0.5), rounds[0].cA.map { Math.round(it * 10.0) / 10.0 })
        val final = rounds.last()
        assertTrue("capsules 1 and 2 (agreeing) gain routing weight", final.cA[0] > 0.55 && final.cA[1] > 0.6)
        assertTrue("capsule 3 (conflicting) loses it", final.cA[2] < 0.45)
    }

    @Test
    fun `ten iterations nearly vote the conflicting capsule out entirely`() {
        val final = CapsuleLab.route(10).last()
        assertTrue(final.cA[0] > 0.98)
        assertTrue(final.cA[1] > 0.98)
        assertTrue("the disagreeing vote's routing weight collapses toward zero", final.cA[2] < 0.02)
    }

    @Test
    fun `and the routed output moves toward the true agreement direction, away from a naive average`() {
        fun angleDeg(v: DoubleArray) = Math.toDegrees(Math.atan2(v[1], v[0]))
        val agreementDir = doubleArrayOf(
            (CapsuleLab.votesA[0][0] + CapsuleLab.votesA[1][0]) / 2,
            (CapsuleLab.votesA[0][1] + CapsuleLab.votesA[1][1]) / 2,
        )
        val angleAgreement = angleDeg(agreementDir)
        val angleNaive = angleDeg(CapsuleLab.naiveAverageA)
        val angle3 = angleDeg(CapsuleLab.rounds.last().vA)
        val angle10 = angleDeg(CapsuleLab.route(10).last().vA)

        assertEquals(6.0, angleAgreement, 0.5)
        assertEquals(24.4, angleNaive, 0.5)
        assertEquals("3 iterations: partway from the naive average toward agreement", 16.7, angle3, 1.0)
        assertEquals("10 iterations: essentially the pure agreement direction", angleAgreement, angle10, 0.5)
    }

    // ── Neural ODEs ────────────────────────────────────────────────────────────

    @Test
    fun `euler's error halves every time the step count doubles -- first order`() {
        val ratios = NeuralOdeLab.eulerErrors.zipWithNext { a, b -> a / b }
        ratios.forEach { assertEquals(2.0, it, 0.15) }
    }

    @Test
    fun `rk4's error drops by sixteen every time the step count doubles -- fourth order`() {
        val ratios = NeuralOdeLab.rk4Errors.zipWithNext { a, b -> a / b }
        ratios.forEach { assertEquals(16.0, it, 3.0) }
        assertTrue(
            "at equal step count rk4 is dramatically more accurate than euler",
            NeuralOdeLab.rk4Errors.last() < NeuralOdeLab.eulerErrors.last() / 1000,
        )
    }

    @Test
    fun `the adjoint method's memory is a fixed multiple smaller, and it does not grow with depth`() {
        assertEquals(3200L, NeuralOdeLab.resNetStoredFloats)
        assertEquals(128L, NeuralOdeLab.adjointStoredFloats)
        assertEquals(25.0, NeuralOdeLab.memoryRatio, 1e-9)
    }

    // ── KAN ────────────────────────────────────────────────────────────────────

    @Test
    fun `at a matched parameter budget, the learnable spline fits the wiggly target far better than the mlp`() {
        assertEquals(16, KanLab.trainedMlp.paramCount)
        assertEquals(16, KanLab.trainedKan.paramCount)
        assertEquals(0.166, KanLab.mlpTestMse, 0.03)
        assertEquals(0.0030, KanLab.kanTestMse, 0.003)
        assertTrue(
            "the per-edge learnable function needs no composition to bend sharply where the mlp's fixed tanh units must",
            KanLab.mlpTestMse / KanLab.kanTestMse > 15.0,
        )
    }
}
