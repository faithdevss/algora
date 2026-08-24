package com.algora.app.feature.topics

import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics
import com.algora.app.feature.topics.content.TopicContentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12 coverage, and the inverse of [DsaFigureCoverageTest]: AI coverage is partial *by design*.
 *
 * The DSA test can ask "does every topic have one" because that phase finished. This phase is a
 * growing pilot against 323 AI topics, so the same question would be false for the whole phase and
 * useless for the part of it that is done. What is worth guarding instead is the pilot list itself —
 * that each id in it really is drawn, and that each id is a real topic rather than a rename that
 * quietly stopped resolving. Shape conformance lives in [FigureShapeTest], which already sweeps every
 * figure in the app regardless of section.
 */
class AiFigureCoverageTest {

    private val aiTopicIds = (
        MachineLearningTopics.topics + DeepLearningTopics.topics +
            NlpTopics.topics + ReinforcementLearningTopics.topics
        ).map { it.id }.toSet()

    /** The pilot, batch by batch. Grows as A2–A5 land; the count assertion below moves with it. */
    private val pilotFigures = listOf(
        // A1 — optimisers, on the new Plot shape.
        "adam", "adamw", "momentum", "rmsprop", "adagrad", "gradient_descent_variants",
        // A2 — activations and losses, still on Plot.
        "relu", "leaky_relu", "sigmoid", "softmax", "activation_functions",
        "cross_entropy_loss", "mse", "hinge_loss",
        // A3 — architectures, on the new LayerStack shape.
        "mlp", "cnn", "transformers", "backpropagation", "batch_normalization", "dropout", "gans", "vae",
        // A4 — attention, on the new Heatmap shape.
        "attention", "multi_head_attention", "self_cross_attention", "positional_encodings",
        // A5 — AI content on the six shapes the DSA phase already had.
        "tokenization", "decision_trees", "kmeans", "q_learning",
        // A6 — regression, on Plot.
        "linear_regression", "polynomial_regression", "ridge_regression", "lasso_regression",
        "elasticnet_regression", "stepwise_regression", "robust_regression", "quantile_regression",
        "bayesian_ridge", "poisson_regression", "isotonic_regression", "lars",
        // A7 — data preprocessing & model evaluation, free-tier priority: Plot except confusion_matrix (Grid).
        "missing_value_imputation", "label_encoding", "one_hot_encoding", "min_max_normalization",
        "confusion_matrix", "accuracy", "precision_recall", "rmse",
        // A8 — free-tier NLP preprocessing, on the existing Strip shape.
        "pos_tagging", "stemming", "stop_words", "text_cleaning",
        // A9 — free-tier RL foundations, on LayerStack, Strip, Heatmap, Graph and Tree.
        "agent_environment", "state_action_reward", "policy", "mdp", "bellman_equation",
        // A10 — free-tier deep learning foundations and CNN mechanics.
        "biological_neuron", "perceptron", "conv_layers", "pooling_layers",
        "early_stopping", "data_augmentation",
        // A11 — free-tier NLP: the encoder, the two metrics, prompting and transfer.
        "bert", "perplexity", "prompt_engineering", "wer", "transfer_learning",
        // A12 — the last of the free tier: three DL, four ML.
        "neural_style_transfer", "segmentation_types", "siamese_networks",
        "apriori", "moving_average", "multi_armed_bandit", "svd",
        // A13 — the first premium batch: linear and kernel classifiers, and the two discriminants.
        "logistic_regression", "knn", "svm", "svm_rbf", "nu_svc", "lda", "qda", "passive_aggressive",
        // A14 — ensembles that average rather than boost.
        "bagging", "random_forest", "extra_trees", "voting", "stacking", "isolation_forest",
        // A15 — boosting, from the original algorithm to the three production libraries.
        "adaboost", "gradient_boosting", "xgboost", "lightgbm", "catboost",
        // A16 — clustering beyond k-means: density, mixtures, linkage, modes and spectra.
        "dbscan", "gmm", "hierarchical_clustering", "mean_shift", "spectral_clustering", "k_medians",
        // A17 — dimensionality reduction, linear and manifold.
        "pca", "ica", "tsne", "umap", "lle", "factor_analysis",
        // M1 — Phase 13's first must-have batch: the classification metrics, each a quantity
        // read against a threshold. See docs/plan/phase-13-must-have-figures.md.
        "roc_curve", "f1_score", "log_loss", "cohens_kappa", "mae",
        // M2 — fit, capacity and the two likelihoods.
        "r_squared", "bias_variance", "outlier_detection", "gaussian_nb", "multinomial_nb",
        // M3 — outputs that are shapes.
        "optics", "hdbscan", "fp_growth", "arima", "exponential_smoothing",
        // M4 — gradients, and what each CNN generation actually changed.
        "vanishing_gradient", "padding_strides", "resnet", "inception", "vgg", "mobilenet",
        // M5 — detection, where the numbers only mean something side by side.
        "rcnn", "yolo", "retinanet", "unet", "mask_rcnn",
    )

    @Test
    fun `every pilot topic has a figure`() {
        val missing = pilotFigures.filter { TopicContentProvider.get(it)?.figure == null }
        assertTrue("Pilot topics with no figure: $missing", missing.isEmpty())
    }

    @Test
    fun `every pilot id is a real AI topic`() {
        val unknown = pilotFigures.filterNot { it in aiTopicIds }
        assertTrue("Pilot ids that match no AI topic: $unknown", unknown.isEmpty())
    }

    @Test
    fun `the pilot is the size the phase was scoped against`() {
        // Keeps the batch table in docs/plan/phase-12-ai-figures.md verifiable rather than folklore.
        assertEquals(134, pilotFigures.size)
    }
}
