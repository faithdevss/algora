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

    // "link" for the rules (X → Y is a link) and "history" for the series; both were still unused in
    // this section, which keeps every ML category on its own glyph.
    val association = Category("ml_association", "Association Rule Learning", Section.ML, 0xFF06B6D4, "link")
    val timeSeries = Category("ml_timeseries", "Time Series Analysis", Section.ML, 0xFF10B981, "history")
    // B8. "browser" reads as a data table — the thing every topic in this category transforms — and
    // it was unused in this section.
    val preprocessing = Category("ml_preprocessing", "Data Preprocessing", Section.ML, 0xFFF97316, "browser")
    // B9. "check" is the verdict every one of these seventeen topics produces, and it was unused in
    // this section.
    val metrics = Category("ml_metrics", "Evaluation Metrics", Section.ML, 0xFF0EA5E9, "check")
    val supervised = Category("ml_supervised", "Supervised", Section.ML, 0xFF6366F1, "robot")

    // `ml_unsupervised` is gone: B5 took its clustering topics and B6 took `pca`, its last one.
    // `ml_supervised` is down to `perceptron`, `bias_variance` and `regularization` — B9 took
    // `model_evaluation` into `ml_metrics` as its landing topic, and B10 takes the rest.
    val all = listOf(
        regression, classification, bayesian, ensemble, clustering, dimReduction, association, timeSeries,
        preprocessing, metrics, supervised,
    )
}
