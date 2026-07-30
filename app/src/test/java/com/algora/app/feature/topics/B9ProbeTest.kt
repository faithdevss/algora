package com.algora.app.feature.topics

import org.junit.Test

class B9ProbeTest {

    @Test
    fun classification() {
        println("── scored ──")
        println("n=${ScoredLab.scored.size} positives ${ScoredLab.positives} majority accuracy ${"%.3f".format(ScoredLab.majorityAccuracy)}")
        ScoredLab.thresholds.forEach { t ->
            val c = ScoredLab.cellsAt(t)
            println("t=$t tp ${c.tp} fp ${c.fp} fn ${c.fn} tn ${c.tn} | acc ${"%.3f".format(c.accuracy)} prec ${"%.3f".format(c.precision)} rec ${"%.3f".format(c.recall)} f1 ${"%.3f".format(c.f1)} kappa ${"%.3f".format(c.kappa)} mcc ${"%.3f".format(c.matthews)}")
        }
        println("best F1 threshold ${ScoredLab.bestF1Threshold} → F1 ${"%.3f".format(ScoredLab.bestF1)}")
        println("AUC trapezoid ${"%.4f".format(ScoredLab.auc)} ranking ${"%.4f".format(ScoredLab.aucByRanking)} avg precision ${"%.4f".format(ScoredLab.averagePrecision)}")
        println("log loss ${"%.4f".format(ScoredLab.logLoss())} brier ${"%.4f".format(ScoredLab.brier())}")
        println("overconfident: AUC ${"%.4f".format(ScoredLab.overconfidentAuc)} log loss ${"%.4f".format(ScoredLab.logLoss(ScoredLab.overconfident))} brier ${"%.4f".format(ScoredLab.brier(ScoredLab.overconfident))}")
        val ce = ScoredLab.confidentError
        println("worst single contribution ${"%.3f".format(ce.worstContribution)} vs mean ${"%.4f".format(ce.meanContribution)} at score ${"%.4f".format(ce.worstScore)}")
        println("calibration:")
        ScoredLab.calibration().forEach { println("  bin ${"%.1f".format(it.lower)} predicted ${"%.3f".format(it.predicted)} observed ${"%.3f".format(it.observed)} n ${it.count}")}
        val prAtRecall = ScoredLab.prCurve.filter { it.first >= 0.8 }.maxByOrNull { it.second }
        println("best precision at recall>=0.8: ${prAtRecall?.second?.let { "%.3f".format(it) }}")
    }

    @Test
    fun regression() {
        println("── regression ──")
        listOf(RegressionMetricsLab.squaredLossFit, RegressionMetricsLab.absoluteLossFit, RegressionMetricsLab.worseThanMean).forEach { f ->
            println("${f.name}: intercept ${"%.3f".format(f.intercept)} slope ${"%.3f".format(f.slope)} " +
                "mse ${"%.3f".format(RegressionMetricsLab.mse(f))} rmse ${"%.3f".format(RegressionMetricsLab.rmse(f))} " +
                "mae ${"%.3f".format(RegressionMetricsLab.mae(f))} r2 ${"%.3f".format(RegressionMetricsLab.rSquared(f))}")
        }
        println("true slope ${RegressionMetricsLab.trueSlope}")
        println("outlier error share ${"%.3f".format(RegressionMetricsLab.outlierErrorShare)} from ${"%.3f".format(RegressionMetricsLab.outlierCountShare)} of the points")
        println("noise columns:")
        RegressionMetricsLab.noiseColumns.forEach { println("  +${it.extraColumns} noise: r2 ${"%.4f".format(it.rSquared)} adj ${"%.4f".format(it.adjusted)}") }
    }

    @Test
    fun impurity() {
        println("── impurity ──")
        ImpurityLab.scores.forEach {
            println("${it.split.name}: gini gain ${"%.4f".format(it.giniGain)} entropy gain ${"%.4f".format(it.entropyGain)} error gain ${"%.4f".format(it.errorGain)}")
        }
        val tied = ImpurityLab.tiedForError
        println("tied for error: ${tied.first.split.name} and ${tied.second.split.name} — error gain both ${"%.4f".format(tied.first.errorGain)}, gini ${"%.4f".format(tied.first.giniGain)} vs ${"%.4f".format(tied.second.giniGain)}")
        println("gini at balance: ${"%.3f".format(ImpurityLab.gini(200, 200))} entropy ${"%.3f".format(ImpurityLab.entropy(200, 200))}")
    }

    @Test
    fun margins() {
        println("── margins ──")
        val a = MarginLab.active
        println("active examples: hinge ${a.hingeActive} of ${a.total}, logistic ${a.logisticActive}")
        listOf(-1.0, 0.0, 0.5, 1.0, 2.0, 5.0, 10.0).forEach { m ->
            println("  margin $m: hinge ${"%.4f".format(MarginLab.hinge(m))} logistic ${"%.4f".format(MarginLab.logistic(m))} " +
                "hinge grad ${MarginLab.hingeGradient(m)} logistic grad ${"%.2e".format(MarginLab.logisticGradientAt(m))}")
        }
    }

    @Test
    fun clustering() {
        println("── clustering ──")
        println("blobs (${ClusterMetricsLab.blobs.size} points):")
        ClusterMetricsLab.blobSweep.forEach { println("  k=${it.k} silhouette ${"%.4f".format(it.silhouette)} DB ${"%.4f".format(it.daviesBouldin)}") }
        println("  best by silhouette ${ClusterMetricsLab.bestBySilhouette(ClusterMetricsLab.blobSweep)} by DB ${ClusterMetricsLab.bestByDaviesBouldin(ClusterMetricsLab.blobSweep)}")
        println("rings (${ClusterMetricsLab.rings.size} points, truth ${ClusterMetricsLab.RING_TRUTH}):")
        ClusterMetricsLab.ringSweep.forEach { println("  k=${it.k} silhouette ${"%.4f".format(it.silhouette)} DB ${"%.4f".format(it.daviesBouldin)}") }
        println("  best by silhouette ${ClusterMetricsLab.bestBySilhouette(ClusterMetricsLab.ringSweep)} by DB ${ClusterMetricsLab.bestByDaviesBouldin(ClusterMetricsLab.ringSweep)}")
        val ringTwo = ClusterMetricsLab.kMeans(ClusterMetricsLab.rings, 2)
        println("  rings at k=2: silhouette ${"%.4f".format(ClusterMetricsLab.silhouetteScore(ClusterMetricsLab.rings, ringTwo))} negatives ${ClusterMetricsLab.negativeCount(ClusterMetricsLab.rings, ringTwo)}")
        val blobThree = ClusterMetricsLab.kMeans(ClusterMetricsLab.blobs, 3)
        println("  blobs inertia k=2 ${"%.4f".format(ClusterMetricsLab.inertia(ClusterMetricsLab.blobs, ClusterMetricsLab.kMeans(ClusterMetricsLab.blobs, 2)))} k=3 ${"%.4f".format(ClusterMetricsLab.inertia(ClusterMetricsLab.blobs, blobThree))} k=4 ${"%.4f".format(ClusterMetricsLab.inertia(ClusterMetricsLab.blobs, ClusterMetricsLab.kMeans(ClusterMetricsLab.blobs, 4)))}")
        println("  blobs at k=3: negatives ${ClusterMetricsLab.negativeCount(ClusterMetricsLab.blobs, blobThree)}")
    }
}
