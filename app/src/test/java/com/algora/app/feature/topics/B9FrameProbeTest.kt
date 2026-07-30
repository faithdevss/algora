package com.algora.app.feature.topics

import org.junit.Test

class B9FrameProbeTest {
    @Test
    fun `b9 labs`() {
        listOf("confusion_matrix", "accuracy", "precision_recall", "f1_score", "silhouette_score", "davies_bouldin").forEach {
            val start = System.nanoTime()
            println("$it: ${pointCloudFrameCount(it)} frames in ${(System.nanoTime() - start) / 1_000_000} ms")
        }
        listOf("roc_curve", "auc", "log_loss", "cohens_kappa", "gini_impurity", "hinge_loss").forEach {
            val start = System.nanoTime()
            println("$it: ${neuralNetFrameCount(it)} frames in ${(System.nanoTime() - start) / 1_000_000} ms")
        }
        listOf("mse", "rmse", "mae", "r_squared", "adjusted_r_squared").forEach {
            val start = System.nanoTime()
            println("$it: ${regressionLabProbe(it)} evaluations in ${(System.nanoTime() - start) / 1_000_000} ms")
        }
    }
}
