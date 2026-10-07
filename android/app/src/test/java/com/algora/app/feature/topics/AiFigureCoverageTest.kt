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
        // M6 — sequence models: the bottleneck vector, the search, the mask, the window,
        // and the direction of the divergence.
        "encoder_decoder", "seq2seq", "gpt", "bptt", "kl_divergence",
        // M7 — NLP where the structure is the point.
        "hmm", "n_grams", "cosine_similarity", "word2vec_skipgram", "dependency_parsing",
        // M8 — the five hubs other topics link into, and the last of the phase.
        "bleu", "lora_qlora", "ppo", "value_iteration", "value_function",
        // Phase 15 — forty more from the remaining Tier A, chosen by inbound cross-links and
        // curriculum weight. See docs/plan/phase-15-priority-figures.md.
        // P1 — RL core: actor-critic family plus the tabular methods, on the page's own gridworld.
        "actor_critic", "sac", "ddpg", "td_learning", "monte_carlo_rl", "policy_iteration",
        "q_function", "sarsa", "discount_factor",
        // P2 — deep learning: transformer internals, activations, training dynamics, detection.
        "feed_forward", "layer_normalization", "flash_attention", "vit", "gelu", "exploding_gradient",
        "lr_schedulers", "alexnet", "bidirectional_rnn", "fast_rcnn", "faster_rcnn",
        // P3 — LLM practice: reasoning, grounding, adaptation, efficiency and long context.
        "chain_of_thought", "hallucination_mitigation", "react", "peft", "quantization",
        "fine_tuning_full", "dpo", "vector_databases", "mistral_mixtral", "long_context", "ssm",
        // P4 — ML metrics, preprocessing and structure, plus two NLP.
        "auc", "silhouette_score", "z_score_standardization", "smote", "gini_impurity",
        "kernel_pca", "bayesian_networks", "rouge", "glove",
        // Phase 16 — fifty more: the rest of Tier A except twelve low-value pages. Each figure is
        // drawn from the topic's visible lab; see docs/plan/phase-16-figures-and-lab-alignment.md.
        // Q1 — RL and model evaluation.
        "dynamic_programming", "exploration_exploitation", "pomdp", "prioritized_replay",
        "adjusted_r_squared", "davies_bouldin",
        // Q2 — classic ML: naive Bayes, selection, association rules, forecasting, sampling.
        "bernoulli_nb", "rfe", "chi_square_selection", "eclat", "autoregression", "sarima", "prophet",
        "mcmc", "restricted_boltzmann_machines", "deep_belief_networks",
        // Q3 — deep learning: activations, CNNs, graphs, generative models, detection, SSMs.
        "swish", "efficientnet", "densenet", "group_normalization", "gcn", "gat", "dcgan", "cyclegan",
        "stylegan", "stable_diffusion", "ssd", "mamba", "rwkv",
        // Q4 — NLP: syntax, similarity, sentiment, embeddings and tokenizers.
        "chunking", "constituency_parsing", "coreference", "pcfg", "jaccard_similarity", "regex_nlp",
        "sentiment_lexicon", "word2vec_cbow", "fasttext", "elmo", "hf_tokenizers",
        // Q5 — pretrained families, evaluation and agents.
        "bart", "distilbert", "roberta", "t5", "xlnet", "gpt3_gpt4", "llama_vicuna", "mmlu",
        "tree_of_thoughts", "ai_agents",
        // R1 — deep RL: value methods and policy gradients.
        "dqn", "double_dqn", "dueling_dqn", "experience_replay", "target_networks", "noisy_nets", "c51",
        "rainbow_dqn", "reinforce", "a2c", "a3c", "gae", "trpo", "td3", "dpg", "max_entropy_rl",
        // R2 — exploration, model-based and multi-agent RL.
        "epsilon_greedy", "ucb", "thompson_sampling", "boltzmann_exploration", "intrinsic_motivation",
        "icm", "rnd", "dyna_q", "world_models", "dreamer", "mbpo", "mcts", "minimax", "alphago",
        "alphazero", "muzero", "self_play", "maddpg", "qmix", "vdn", "meta_rl",
        // R3 — offline, imitation and RLHF.
        "offline_rl", "cql", "iql", "decision_transformer", "imitation_learning", "irl", "gail", "rlhf",
        // R4 — ML, deep learning and NLP stubs.
        "rnn", "lstm_gru", "neural_network_basics", "autoencoders", "diffusion_models", "regularization",
        "model_evaluation", "naive_bayes", "bow_tfidf", "lemmatization", "ner", "rag", "bpe",
        // R5 — the twelve Phase 16 skipped.
        "deepfakes", "complement_nb", "categorical_nb", "k_modes", "birch", "affinity_propagation",
        "incremental_pca", "meteor", "kan", "neural_odes", "capsule_networks", "selu",
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
        assertEquals(309, pilotFigures.size)
    }
}
