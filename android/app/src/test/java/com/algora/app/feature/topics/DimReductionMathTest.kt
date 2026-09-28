package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pins the properties the B6 lab copy leans on.
 *
 * Every frame in the eight Dimensionality Reduction simulations quotes a number computed at build
 * time, so the narration cannot contradict the code. What it *can* do is stop being interesting:
 * if a dataset or a parameter drifts so that kernel PCA no longer separates the rings, or LLE stops
 * failing at k = 6, the frames will still be internally consistent and the topic will have lost its
 * point. These assertions are the guard for that — the measured values are in the comments, and the
 * bounds are loose enough to survive a re-tune and tight enough to catch a regression.
 *
 * The discipline comes from the phase's history: five earlier batches shipped copy that was
 * plausible and wrong (an inverted Complement NB sign, an XGBoost gain that was negative where the
 * frame took it, a `twoMoons` geometry that made spectral clustering lose to k-means). Each was
 * caught by measuring first. This file is that measurement, kept.
 */
class DimReductionMathTest {

    @Test
    fun `jacobi eigendecomposition reproduces the matrix it was given`() {
        val a = arrayOf(
            doubleArrayOf(4.0, 1.0, 2.0),
            doubleArrayOf(1.0, 3.0, 0.5),
            doubleArrayOf(2.0, 0.5, 5.0),
        )
        val eigen = jacobiEigen(a)

        // Descending, unit-norm, and Av = λv for every pair.
        assertTrue(eigen.values[0] > eigen.values[1] && eigen.values[1] > eigen.values[2])
        eigen.vectors.forEachIndexed { k, v ->
            assertEquals(1.0, sqrt(v.sumOf { it * it }), 1e-9)
            val av = DoubleArray(3) { r -> (0..2).sumOf { c -> a[r][c] * v[c] } }
            (0..2).forEach { assertEquals(eigen.values[k] * v[it], av[it], 1e-9) }
        }
    }

    @Test
    fun `kernel PCA separates the rings where linear PCA cannot`() {
        val rings = concentricRings
        val mean = meanOf(rings)
        val pc1 = jacobiEigen(covariance2(rings)).vectors[0]
        val linearScores = DoubleArray(rings.size) { i ->
            (rings[i].x - mean.x) * pc1[0] + (rings[i].y - mean.y) * pc1[1]
        }

        // Measured: linear 0.75, kernel (γ = 4) 1.00. The topic exists because of that gap.
        val linear = bestThresholdAccuracy(linearScores, InnerRingCount)
        val kernel = bestThresholdAccuracy(kernelPca(rings, 4.0).components[0], InnerRingCount)
        assertTrue("linear PC1 should not separate the rings, got $linear", linear < 0.85)
        assertTrue("kernel PC1 should separate the rings, got $kernel", kernel > 0.95)

        // And γ has to matter in both directions, or the "the bandwidth is the model" frame is empty.
        // Measured: 0.73 at γ = 0.5, 0.83 at γ = 16.
        assertTrue(bestThresholdAccuracy(kernelPca(rings, 0.5).components[0], InnerRingCount) < kernel)
        assertTrue(bestThresholdAccuracy(kernelPca(rings, 16.0).components[0], InnerRingCount) < kernel)
    }

    @Test
    fun `incremental PCA converges on the batch answer and lands on it exactly`() {
        val steps = incrementalPca(correlatedCloudPts, 4)
        // Measured: 35.0° after four rows, then 5.8, 4.6, 2.3, 0.0.
        assertTrue("first batch should be visibly off", steps.first().degreesFromBatch > 10.0)
        assertEquals("the accumulator form is exact once every row has passed", 0.0, steps.last().degreesFromBatch, 1e-6)
        assertEquals(correlatedCloudPts.size, steps.last().seen)
    }

    @Test
    fun `truncating the SVD leaves exactly the discarded singular value`() {
        val mean = meanOf(correlatedCloudPts)
        val centered = correlatedCloudPts.map { Pt(it.x - mean.x, it.y - mean.y) }
        val svd = svd2(centered)

        // Eckart-Young, checked rather than quoted. Measured: both 0.1711.
        assertEquals(svd.singularValues[1], svd.rank1Error, 1e-4)
        assertTrue(svd.singularValues[0] > svd.singularValues[1])
    }

    @Test
    fun `an uncentred SVD points at the mean instead of the spread`() {
        val mean = meanOf(correlatedCloudPts)
        val centered = correlatedCloudPts.map { Pt(it.x - mean.x, it.y - mean.y) }
        val centeredAngle = svd2(centered).rightVectors[0].let { axisAngleDegrees(it[0], it[1]) }
        val rawAngle = svd2(correlatedCloudPts).rightVectors[0].let { axisAngleDegrees(it[0], it[1]) }
        val meanAngle = axisAngleDegrees(mean.x.toDouble(), mean.y.toDouble())

        // Measured: centred 34.4°, raw 43.9°, mean direction 45.5°.
        assertTrue(
            "the raw SVD's v1 should sit closer to the mean direction than the centred one does",
            axisSeparation(rawAngle, meanAngle) < axisSeparation(centeredAngle, meanAngle),
        )
    }

    @Test
    fun `FastICA recovers the mixing directions that PCA structurally cannot`() {
        val ica = fastIca(mixedSources)
        val trueA = axisAngleDegrees(mixingMatrix[0][0], mixingMatrix[1][0])
        val trueB = axisAngleDegrees(mixingMatrix[0][1], mixingMatrix[1][1])

        // Measured: 19.3° and 58.2° true; 19.26° and 59.32° recovered.
        val errors = ica.mixingDirections.map { d ->
            val angle = axisAngleDegrees(d[0], d[1])
            minOf(axisSeparation(angle, trueA), axisSeparation(angle, trueB))
        }
        errors.forEach { assertTrue("recovered direction off by $it°", it < 5.0) }

        // The two recovered directions must be different sources, not the same one twice.
        val recoveredSeparation = axisSeparation(
            axisAngleDegrees(ica.mixingDirections[0][0], ica.mixingDirections[0][1]),
            axisAngleDegrees(ica.mixingDirections[1][0], ica.mixingDirections[1][1]),
        )
        assertEquals(axisSeparation(trueA, trueB), recoveredSeparation, 5.0)

        // And the premise: the true mixing is not orthogonal, so PCA could never have found it.
        assertTrue(abs(axisSeparation(trueA, trueB) - 90.0) > 30.0)
    }

    @Test
    fun `factor analysis reproduces the correlations and PCA does not`() {
        val fa = oneFactorAnalysis(factorSamples)
        var faResidual = 0.0
        var pcaResidual = 0.0
        for (i in 0..2) for (j in 0..2) {
            if (i == j) continue
            faResidual += abs(fa.loadings[i] * fa.loadings[j] - fa.correlation[i][j])
            pcaResidual += abs(fa.pcaLoadings[i] * fa.pcaLoadings[j] - fa.correlation[i][j])
        }

        // Measured: FA 0.0000 (exactly identified), PCA 0.85. This is the frame's whole claim.
        assertEquals("one factor over three variables reproduces r exactly", 0.0, faResidual, 1e-6)
        assertTrue("PCA's rank-1 reconstruction should miss the correlations, got $pcaResidual", pcaResidual > 0.3)

        // The noisy variable is the one the model charges to ε. Measured: ψ₂ = 0.87.
        assertTrue(fa.uniquenesses[1] > fa.uniquenesses[0])
        assertTrue(fa.uniquenesses[1] > fa.uniquenesses[2])
    }

    @Test
    fun `t-SNE keeps neighbourhoods and destroys cluster size`() {
        val input = unevenClusters
        val out = tsne(input).last().embedding

        fun spread(points: List<Pt>, cluster: Int): Double {
            val members = points.indices.filter { unevenClusterLabel(it) == cluster }
            val cx = members.map { points[it].x }.average().toFloat()
            val cy = members.map { points[it].y }.average().toFloat()
            return members.map { dist(points[it], Pt(cx, cy)).toDouble() }.average()
        }

        // Measured: 0.67 of each point's five nearest neighbours survive.
        assertTrue(neighbourPreservation(input, out) > 0.5)

        // The dataset's premise: cluster 2 is the loosest in the input by a wide margin.
        assertTrue(spread(input, 2) > 2 * spread(input, 0))
        // And the distortion the copy is about: it is no longer the loosest in the map.
        // Measured: input 0.040 / 0.032 / 0.117 → map 0.200 / 0.144 / 0.101.
        assertTrue(
            "t-SNE is supposed to equalise cluster size; the loose cluster is still the largest",
            spread(out, 2) < spread(out, 0),
        )
    }

    @Test
    fun `UMAP holds more of the global arrangement than t-SNE on the same points`() {
        val input = unevenClusters
        val umap = umapLayout(input).embedding
        val tsne = tsne(input).last().embedding

        // Measured: preservation 0.80 vs 0.67; separation ratio 6.55 vs 2.10 against the input's 4.97.
        assertTrue(neighbourPreservation(input, umap) > neighbourPreservation(input, tsne))
        assertTrue(
            separationRatio(umap, ::unevenClusterLabel) > separationRatio(tsne, ::unevenClusterLabel),
        )
    }

    @Test
    fun `LLE recovers the spiral's parameter and PCA does not`() {
        val curve = spiralCurve
        val order = DoubleArray(curve.size) { it.toDouble() }
        val lleRho = absSpearman(lle(curve, 4).embedding, order)

        val mean = meanOf(curve)
        val pc1 = jacobiEigen(covariance2(curve)).vectors[0]
        val projection = DoubleArray(curve.size) { i ->
            (curve[i].x - mean.x) * pc1[0] + (curve[i].y - mean.y) * pc1[1]
        }
        val pcaRho = absSpearman(projection, order)

        // Measured: LLE 0.99 at k = 4, PCA 0.47 — a straight projection folds the spiral onto itself.
        assertTrue("LLE should recover the ordering, got $lleRho", lleRho > 0.9)
        assertTrue("PCA should not, got $pcaRho", pcaRho < 0.7)
    }

    @Test
    fun `LLE collapses when k lets neighbourhoods reach across the spiral`() {
        val curve = spiralCurve
        val order = DoubleArray(curve.size) { it.toDouble() }

        fun shortCircuits(k: Int) =
            lle(curve, k).neighbours.withIndex().sumOf { (i, nb) -> nb.count { abs(it - i) > 3 } }

        // Measured: k = 4 has 2 short-circuit links and ρ = 0.99; k = 6 has 18 and ρ = 0.01.
        assertTrue(shortCircuits(6) > shortCircuits(4))
        assertTrue(
            "the k = 6 failure frame needs the failure to actually happen",
            absSpearman(lle(curve, 6).embedding, order) < 0.5,
        )
    }

    @Test
    fun `every layout the labs draw stays inside the unit square`() {
        // SimulationFrameTest checks the frames; this checks the primitives they are built from, so
        // a NaN or an escaped point is attributed to the estimator rather than to the drawing code.
        val layouts = listOf(
            "unevenClusters" to unevenClusters,
            "tsne" to tsne(unevenClusters).last().embedding,
            "umap" to umapLayout(unevenClusters).embedding,
            "kernelPca" to fitToUnit(concentricRings),
            "spiral" to spiralCurve,
        )
        layouts.forEach { (name, points) ->
            points.forEach {
                assertTrue("$name produced ${it.x}, ${it.y}", !it.x.isNaN() && !it.y.isNaN())
                assertTrue("$name escapes the unit square at ${it.x}, ${it.y}", it.x in -0.05f..1.05f && it.y in -0.05f..1.05f)
            }
        }
    }
}
