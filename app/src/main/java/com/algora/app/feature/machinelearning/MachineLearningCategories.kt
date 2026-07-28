package com.algora.app.feature.machinelearning

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Section

// Colors/icons pulled verbatim from docs/design/Algora.dc.html's cats()['ml'].
//
// Phase 9 expands this beyond the mock's two buckets. The mock never fleshed out ML — its detailed
// AI taxonomy is RL, which uses nine categories — and docs/topics.ai.md lists twelve ML sub-sections
// that will not fit in "Supervised" and "Unsupervised". Categories are added per batch as their
// topics land, so `all` grows over the phase rather than arriving empty.
object MachineLearningCategories {
    val regression = Category("ml_regression", "Regression", Section.ML, 0xFF6366F1, "trend")
    val classification = Category("ml_classification", "Classification", Section.ML, 0xFF8B5CF6, "target")
    val bayesian = Category("ml_bayesian", "Bayesian Algorithms", Section.ML, 0xFF14B8A6, "flask")
    val ensemble = Category("ml_ensemble", "Ensemble Methods", Section.ML, 0xFFF59E0B, "stack")
    val clustering = Category("ml_clustering", "Clustering", Section.ML, 0xFF3B82F6, "share")

    // "chart" is the scree plot — the one picture every method in this category produces. The other
    // apt name, "stack", already belongs to Ensemble Methods and would render the same glyph twice
    // in one browser.
    val dimReduction = Category("ml_dimreduction", "Dimensionality Reduction", Section.ML, 0xFFEC4899, "chart")
    val supervised = Category("ml_supervised", "Supervised", Section.ML, 0xFF6366F1, "robot")

    // `ml_unsupervised` is gone: B5 took its clustering topics and B6 took `pca`, its last one.
    // `ml_supervised` still holds the four topics B9 and B10 will redistribute.
    val all = listOf(regression, classification, bayesian, ensemble, clustering, dimReduction, supervised)
}
