package com.algora.app.core.data

import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Makes `docs/topics.ai.md` executable, the way [AlgoTaxonomyCoverageTest] does for the DSA doc.
 *
 * The difference is that the AI doc is *not yet* covered. Phase 9 is authoring its way through 226
 * missing topics, so this map is deliberately partial: an entry with no topic behind it maps to an
 * empty list, and `covered doc entries` is a number this test pins. Every batch that lands has to
 * move that number, which turns the phase's headline question — "how much of the doc do we ship?" —
 * from a hand recount in a markdown table into a test result.
 *
 * Keys are the doc's own `#` sections, `**bold**` headings and entry names, verbatim, so this file
 * can be diffed against the doc by eye. Section nesting is not decoration: the doc reuses the
 * heading "Recurrent Neural Networks" under both Deep Learning and NLP, and lists several entries
 * (LSTM, RLHF, The Perceptron, MDP) under more than one section on purpose.
 *
 * ## What counts as covered
 *
 * Strictly: a doc entry is covered when a topic exists whose *subject is that entry*. Several app
 * topics are umbrellas written before the doc was broken out into sub-sections — `activation_functions`,
 * `cnn`, `llms`, `word_embeddings`, `model_evaluation`, `naive_bayes` — and each of those stands in
 * front of a whole doc block. They are counted as covering nothing, because counting an umbrella as
 * ten entries is exactly the estimate this test exists to replace. Their batches (B9, C2, C3, D3,
 * D4) will author the real entries and keep the umbrella as the category's landing topic.
 *
 * Cross-section reuse is allowed and expected: the doc's DL → Deep RL block is served entirely by
 * the RL section, its NLP → Statistical block leans on `naive_bayes` and the DSA `edit_distance`,
 * and `perceptron` (an ML topic) answers a DL entry.
 */
class AiTaxonomyCoverageTest {

    private val taxonomy: Map<String, Map<String, Map<String, List<String>>>> = linkedMapOf(
        "Machine Learning" to linkedMapOf(
            "Regression (Predicting Numbers)" to linkedMapOf(
                "Linear Regression (OLS)" to listOf("linear_regression"),
                "Polynomial Regression" to listOf("polynomial_regression"),
                "Ridge Regression (L2)" to listOf("ridge_regression"),
                "Lasso Regression (L1)" to listOf("lasso_regression"),
                "ElasticNet Regression" to listOf("elasticnet_regression"),
                "Stepwise Regression" to listOf("stepwise_regression"),
                "Robust Regression (RANSAC)" to listOf("robust_regression"),
                "Quantile Regression" to listOf("quantile_regression"),
                "Bayesian Ridge Regression" to listOf("bayesian_ridge"),
                "Poisson Regression" to listOf("poisson_regression"),
                "Isotonic Regression" to listOf("isotonic_regression"),
                "Least Angle Regression (LARS)" to listOf("lars"),
            ),
            "Classification (Categorizing)" to linkedMapOf(
                "Logistic Regression" to listOf("logistic_regression"),
                "K-Nearest Neighbors (KNN)" to listOf("knn"),
                "Decision Trees" to listOf("decision_trees"),
                "Support Vector Machines (Linear)" to listOf("svm"),
                "SVM (Radial Basis Function)" to listOf("svm_rbf"),
                "Nu-Support Vector Classification" to listOf("nu_svc"),
                "Linear Discriminant Analysis (LDA)" to listOf("lda"),
                "Quadratic Discriminant Analysis (QDA)" to listOf("qda"),
                "Passive Aggressive Classifier" to listOf("passive_aggressive"),
            ),
            "Bayesian Algorithms" to linkedMapOf(
                // `naive_bayes` is the umbrella in front of the five variants; it covers none of
                // them on its own. It answers the NLP doc's "Naive Bayes Classifier" instead.
                "Gaussian Naive Bayes" to listOf("gaussian_nb"),
                "Multinomial Naive Bayes" to listOf("multinomial_nb"),
                "Bernoulli Naive Bayes" to listOf("bernoulli_nb"),
                "Complement Naive Bayes" to listOf("complement_nb"),
                "Categorical Naive Bayes" to listOf("categorical_nb"),
                "Bayesian Networks" to listOf("bayesian_networks"),
                "Markov Chain Monte Carlo (MCMC)" to listOf("mcmc"),
            ),
            "Ensemble Methods (Boosting)" to linkedMapOf(
                "Random Forests" to listOf("random_forest"),
                "AdaBoost" to listOf("adaboost"),
                "Gradient Boosting Machines (GBM)" to listOf("gradient_boosting"),
                "XGBoost (Extreme Gradient Boosting)" to listOf("xgboost"),
                "LightGBM" to listOf("lightgbm"),
                "CatBoost" to listOf("catboost"),
                "Extra Trees Classifier" to listOf("extra_trees"),
                "Voting Classifiers" to listOf("voting"),
                "Stacking & Blending" to listOf("stacking"),
                "Bagging (Bootstrap Aggregating)" to listOf("bagging"),
                "Isolation Forest (Anomaly Detection)" to listOf("isolation_forest"),
            ),
            "Clustering (Unsupervised)" to linkedMapOf(
                "K-Means Clustering" to listOf("kmeans"),
                "K-Medians" to listOf("k_medians"),
                "K-Modes" to listOf("k_modes"),
                "Hierarchical (Agglomerative)" to listOf("hierarchical_clustering"),
                "Hierarchical (Divisive)" to listOf("hierarchical_divisive"),
                "DBSCAN" to listOf("dbscan"),
                "HDBSCAN" to listOf("hdbscan"),
                "OPTICS" to listOf("optics"),
                "Mean Shift Clustering" to listOf("mean_shift"),
                "BIRCH" to listOf("birch"),
                "Affinity Propagation" to listOf("affinity_propagation"),
                "Spectral Clustering" to listOf("spectral_clustering"),
                "Gaussian Mixture Models (GMM)" to listOf("gmm"),
            ),
            "Dimensionality Reduction" to linkedMapOf(
                "Principal Component Analysis (PCA)" to listOf("pca"),
                "Kernel PCA" to listOf("kernel_pca"),
                "Incremental PCA" to listOf("incremental_pca"),
                "t-SNE" to listOf("tsne"),
                "UMAP" to listOf("umap"),
                "Singular Value Decomposition (SVD)" to listOf("svd"),
                "Independent Component Analysis (ICA)" to listOf("ica"),
                "Factor Analysis" to listOf("factor_analysis"),
                "Locally Linear Embedding (LLE)" to listOf("lle"),
            ),
            "Neural Network Foundations" to linkedMapOf(
                "The Perceptron" to listOf("perceptron"),
                "Multi-Layer Perceptron (MLP)" to listOf("mlp"),
                "Backpropagation" to listOf("backpropagation"),
                // One topic, "Gradient Descent Variants", whose subject is the batch/mini/stochastic
                // split and the adaptive optimizers built on it. C8 breaks the optimizers out.
                "Stochastic Gradient Descent (SGD)" to listOf("gradient_descent_variants"),
                "Activation Functions (ReLU, Sigmoid)" to listOf("activation_functions"),
                "Optimizers (Adam, RMSprop)" to listOf("gradient_descent_variants"),
                "Dropout & Regularization" to listOf("dropout", "regularization"),
                "Batch Normalization" to listOf("batch_normalization"),
                "Autoencoders" to listOf("autoencoders"),
                "Restricted Boltzmann Machines" to emptyList(),         // B10
                "Deep Belief Networks" to emptyList(),                  // B10
            ),
            "Association Rule Learning" to linkedMapOf(
                "Apriori Algorithm" to listOf("apriori"),
                "Eclat Algorithm" to listOf("eclat"),
                "FP-Growth Algorithm" to listOf("fp_growth"),
            ),
            "Time Series Analysis" to linkedMapOf(
                "Moving Average (MA)" to listOf("moving_average"),
                "Autoregression (AR)" to listOf("autoregression"),
                "ARIMA" to listOf("arima"),
                "SARIMA (Seasonal)" to listOf("sarima"),
                "Exponential Smoothing (Holt-Winters)" to listOf("exponential_smoothing"),
                "Prophet (by Meta)" to listOf("prophet"),
            ),
            "Data Preprocessing Techniques" to linkedMapOf(
                "Normalization (Min-Max)" to emptyList(),               // B8
                "Standardization (Z-Score)" to emptyList(),             // B8
                "Label Encoding" to emptyList(),                        // B8
                "One-Hot Encoding" to emptyList(),                      // B8
                "Missing Value Imputation" to emptyList(),              // B8
                "SMOTE (Oversampling)" to emptyList(),                  // B8
                "Outlier Detection (IQR/Z)" to emptyList(),             // B8
                "Feature Selection (Chi-Square)" to emptyList(),        // B8
                "Recursive Feature Elimination" to emptyList(),         // B8
            ),
            // `model_evaluation` is the umbrella in front of this whole block. B9 authors the 17
            // entries and keeps it as the category's landing topic.
            "Model Evaluation Metrics" to linkedMapOf(
                "Confusion Matrix" to emptyList(),                      // B9
                "Accuracy" to emptyList(),                              // B9
                "Mean Squared Error (MSE)" to emptyList(),              // B9
                "Root Mean Squared Error (RMSE)" to emptyList(),        // B9
                "Mean Absolute Error (MAE)" to emptyList(),             // B9
                "R-Squared (R²)" to emptyList(),                        // B9
                "Adjusted R²" to emptyList(),                           // B9
                "Precision & Recall" to emptyList(),                    // B9
                "F1 Score" to emptyList(),                              // B9
                "ROC Curve" to emptyList(),                             // B9
                "AUC Score" to emptyList(),                             // B9
                "Log Loss (Cross-Entropy)" to emptyList(),              // B9
                "Gini Impurity" to emptyList(),                         // B9
                "Hinge Loss" to emptyList(),                            // B9 / C8
                "Cohen's Kappa" to emptyList(),                         // B9
                "Silhouette Score" to emptyList(),                      // B9
                "Davies-Bouldin Index" to emptyList(),                  // B9
            ),
            "RL Fundamentals" to linkedMapOf(
                "Multi-Armed Bandit" to emptyList(),                    // B10
                "Markov Decision Process (MDP)" to listOf("mdp"),
                "Q-Learning" to listOf("q_learning"),
                "SARSA" to listOf("sarsa"),
                "Thompson Sampling" to listOf("thompson_sampling"),
                "Upper Confidence Bound (UCB)" to listOf("ucb"),
            ),
        ),
        "Deep Learning" to linkedMapOf(
            "Neural Network Basics" to linkedMapOf(
                "The Biological Neuron" to listOf("biological_neuron"),
                "The Perceptron" to listOf("perceptron"),
                "Multi-Layer Perceptron (MLP)" to listOf("mlp"),
                "Feedforward Networks" to listOf("neural_network_basics"),
                "Backpropagation Algorithm" to listOf("backpropagation"),
                "The Vanishing Gradient Problem" to listOf("vanishing_gradient"),
                "The Exploding Gradient Problem" to listOf("exploding_gradient"),
            ),
            // `activation_functions` is the umbrella; C2 authors the ten.
            "Activation Functions" to linkedMapOf(
                "Sigmoid" to listOf("sigmoid"),
                "Tanh (Hyperbolic Tangent)" to listOf("tanh"),
                "ReLU (Rectified Linear Unit)" to listOf("relu"),
                "Leaky ReLU" to listOf("leaky_relu"),
                "Parametric ReLU (PReLU)" to listOf("prelu"),
                "ELU (Exponential Linear Unit)" to listOf("elu"),
                "SELU (Scaled ELU)" to listOf("selu"),
                "Swish (by Google)" to listOf("swish"),
                "GELU (Gaussian Error Linear Unit)" to listOf("gelu"),
                "Softmax (Output Layer)" to listOf("softmax"),
            ),
            // `cnn` is the umbrella; C3 authors the mechanics and the architectures.
            "Convolutional Neural Networks (CNN)" to linkedMapOf(
                "Convolution Layers" to emptyList(),                    // C3
                "Pooling Layers (Max/Average)" to emptyList(),          // C3
                "Padding & Strides" to emptyList(),                     // C3
                "LeNet-5 (The Original)" to emptyList(),                // C3
                "AlexNet (The Breakthrough)" to emptyList(),            // C3
                "VGG-16 / VGG-19" to emptyList(),                       // C3
                "Inception (GoogLeNet)" to emptyList(),                 // C3
                "ResNet (Residual Connections)" to emptyList(),         // C3
                "DenseNet" to emptyList(),                              // C3
                "MobileNet (Lightweight)" to emptyList(),               // C3
                "EfficientNet" to emptyList(),                          // C3
                "Vision Transformers (ViT)" to emptyList(),             // C3
            ),
            "Object Detection & Vision Tasks" to linkedMapOf(
                "R-CNN" to emptyList(),                                 // C4
                "Fast R-CNN" to emptyList(),                            // C4
                "Faster R-CNN" to emptyList(),                          // C4
                "YOLO (You Only Look Once) V1–V8" to emptyList(),       // C4
                "SSD (Single Shot Detector)" to emptyList(),            // C4
                "RetinaNet (Focal Loss)" to emptyList(),                // C4
                "U-Net (Medical Segmentation)" to emptyList(),          // C4
                "Mask R-CNN (Instance Seg.)" to emptyList(),            // C4
                "Semantic vs Instance Segmentation" to emptyList(),     // C4
            ),
            "Recurrent Neural Networks (RNN)" to linkedMapOf(
                "Vanilla RNNs" to listOf("rnn"),
                "BPTT (Backprop Through Time)" to emptyList(),          // C5
                "LSTM (Long Short-Term Memory)" to listOf("lstm_gru"),
                "GRU (Gated Recurrent Unit)" to listOf("lstm_gru"),
                "Bidirectional RNNs" to emptyList(),                    // C5
                "Encoder-Decoder Architecture" to emptyList(),          // C5
                "Seq2Seq Models" to emptyList(),                        // C5
            ),
            "Transformers & LLMs" to linkedMapOf(
                "The Attention Mechanism" to listOf("attention"),
                "Self-Attention vs Cross-Attention" to emptyList(),     // C6
                "Multi-Head Attention" to emptyList(),                  // C6
                "The Transformer (Attention Is All You Need)" to listOf("transformers"),
                "BERT (Bidirectional Encoder)" to emptyList(),          // C6
                "GPT (Generative Pre-trained Transformer)" to emptyList(),   // C6
                "T5 (Text-to-Text)" to emptyList(),                     // C6
                "RoBERTa" to emptyList(),                               // C6
                "DistilBERT" to emptyList(),                            // C6
                "Hugging Face Tokenizers" to emptyList(),               // C6
                "RLHF (Reinforcement Learning from Human Feedback)" to listOf("rlhf"),
            ),
            "Generative Deep Learning" to linkedMapOf(
                "Variational Autoencoders (VAE)" to emptyList(),        // C7
                "GANs (Generative Adversarial Networks)" to listOf("gans"),
                "DCGAN (Deep Convolutional GAN)" to emptyList(),        // C7
                "CycleGAN (Image-to-Image)" to emptyList(),             // C7
                "StyleGAN" to emptyList(),                              // C7
                "Diffusion Models (DDPM)" to listOf("diffusion_models"),
                "Stable Diffusion Architecture" to emptyList(),         // C7
                "Neural Style Transfer" to emptyList(),                 // C7
                "DeepFakes (Concept)" to emptyList(),                   // C7
            ),
            // Served entirely by the Reinforcement Learning section — cross-section reuse, not a gap.
            "Deep Reinforcement Learning" to linkedMapOf(
                "Deep Q-Networks (DQN)" to listOf("dqn"),
                "Double DQN" to listOf("double_dqn"),
                "Dueling DQN" to listOf("dueling_dqn"),
                "Policy Gradients" to listOf("reinforce"),
                "Actor-Critic Methods (A2C/A3C)" to listOf("actor_critic", "a2c", "a3c"),
                "PPO (Proximal Policy Optimization)" to listOf("ppo"),
                "AlphaGo / AlphaZero Architecture" to listOf("alphago", "alphazero"),
            ),
            "Optimizers & Training" to linkedMapOf(
                "Gradient Descent (Batch/Mini/Stochastic)" to listOf("gradient_descent_variants"),
                "Momentum" to emptyList(),                              // C8
                "AdaGrad" to emptyList(),                               // C8
                "RMSprop" to emptyList(),                               // C8
                "Adam (Adaptive Moment Est.)" to emptyList(),           // C8
                "AdamW (Weight Decay)" to emptyList(),                  // C8
                "Learning Rate Schedulers" to emptyList(),              // C8
                "Cross-Entropy Loss" to emptyList(),                    // C8
                "Binary Cross-Entropy" to emptyList(),                  // C8
                "Hinge Loss" to emptyList(),                            // C8
                "Kullback-Leibler (KL) Divergence" to emptyList(),      // C8
            ),
            "Regularization Techniques" to linkedMapOf(
                "L1 / L2 Regularization" to listOf("regularization"),
                "Dropout" to listOf("dropout"),
                "Data Augmentation" to emptyList(),                     // C9
                "Early Stopping" to emptyList(),                        // C9
                "Batch Normalization" to listOf("batch_normalization"),
                "Layer Normalization" to emptyList(),                   // C9
                "Group Normalization" to emptyList(),                   // C9
            ),
            "Specialized & Graph Networks" to linkedMapOf(
                "Siamese Networks (One-Shot Learning)" to emptyList(),  // C9
                "Graph Convolutional Networks (GCN)" to emptyList(),    // C9
                "Graph Attention Networks (GAT)" to emptyList(),        // C9
                "Capsule Networks" to emptyList(),                      // C9
                "Neural ODEs" to emptyList(),                           // C9
                "Kolmogorov-Arnold Networks (KAN)" to emptyList(),      // C9
            ),
        ),
        "NLP" to linkedMapOf(
            "Text Preprocessing (The Atoms)" to linkedMapOf(
                "Tokenization (Word vs Sentence)" to listOf("tokenization"),
                "Stop Word Removal" to emptyList(),                     // D1
                "Stemming (Porter/Snowball)" to listOf("stemming"),
                "Lemmatization (WordNet)" to listOf("lemmatization"),
                "Lowercasing & Cleaning" to emptyList(),                // D1
                "Regular Expressions (RegEx)" to emptyList(),           // D1
                "N-Grams (Unigram, Bigram)" to emptyList(),             // D1
                "Subword Tokenization (BPE, WordPiece)" to listOf("bpe"),
            ),
            "Statistical NLP (Pre-Deep Learning)" to linkedMapOf(
                "Bag of Words (BoW)" to listOf("bow_tfidf"),
                "TF-IDF (Term Frequency)" to listOf("bow_tfidf"),
                "Naive Bayes Classifier" to listOf("naive_bayes"),
                "Hidden Markov Models (HMM)" to emptyList(),            // D1
                "Probabilistic Context-Free Grammars" to emptyList(),   // D1
                // Satisfied from the DSA taxonomy; D1 links rather than duplicating.
                "Edit Distance (Levenshtein)" to listOf("edit_distance"),
                "Cosine Similarity" to emptyList(),                     // D1
                "Jaccard Similarity" to emptyList(),                    // D1
            ),
            "Syntactic & Semantic Analysis" to linkedMapOf(
                "Part-of-Speech (POS) Tagging" to emptyList(),          // D2
                "Named Entity Recognition (NER)" to listOf("ner"),
                "Dependency Parsing" to emptyList(),                    // D2
                "Constituency Parsing" to emptyList(),                  // D2
                "Chunking" to emptyList(),                              // D2
                "Coreference Resolution" to emptyList(),                // D2
                "Sentiment Analysis (Lexicon Based)" to emptyList(),    // D2
            ),
            // `word_embeddings` is the umbrella; D3 authors the five models under it.
            "Word Embeddings (Vectorization)" to linkedMapOf(
                "One-Hot Encoding" to emptyList(),                      // B8
                "Word2Vec (CBOW)" to emptyList(),                       // D3
                "Word2Vec (Skip-Gram)" to emptyList(),                  // D3
                "GloVe (Global Vectors)" to emptyList(),                // D3
                "FastText (Subword info)" to emptyList(),               // D3
                "ELMo (Contextual Embeddings)" to emptyList(),          // D3
            ),
            "Recurrent Neural Networks" to linkedMapOf(
                "Vanilla RNNs" to listOf("rnn"),
                "LSTMs (Long Short-Term Memory)" to listOf("lstm_gru", "rnn_lstm"),
                "GRUs (Gated Recurrent Units)" to listOf("lstm_gru"),
                "Bidirectional LSTMs" to emptyList(),                   // C5
                "Sequence-to-Sequence (Seq2Seq)" to emptyList(),        // C5
                "Encoder-Decoder Architecture" to emptyList(),          // C5
            ),
            "The Transformer Architecture" to linkedMapOf(
                "The Attention Mechanism" to listOf("attention"),
                "Self-Attention" to emptyList(),                        // C6
                "Multi-Head Attention" to emptyList(),                  // C6
                "Positional Encodings" to emptyList(),                  // D4
                "Layer Normalization" to emptyList(),                   // C9
                "Feed-Forward Networks" to emptyList(),                 // D4
            ),
            // `llms` is the umbrella in front of this whole block; C6 and D4 author the models.
            "Pre-trained Language Models" to linkedMapOf(
                "BERT (Encoder Only)" to emptyList(),                   // C6
                "GPT-2 (Decoder Only)" to emptyList(),                  // C6
                "T5 (Text-to-Text)" to emptyList(),                     // C6
                "RoBERTa & DistilBERT" to emptyList(),                  // C6
                "BART" to emptyList(),                                  // D4
                "XLNet" to emptyList(),                                 // D4
                "GPT-3 & GPT-4" to emptyList(),                         // D4
                "LLaMA & Vicuna" to emptyList(),                        // D4
                "Mistral & Mixtral (MoE)" to emptyList(),               // D4
                "Claude & Gemini" to emptyList(),                       // D4
            ),
            "Modern LLM Techniques" to linkedMapOf(
                "Prompt Engineering (Zero/Few Shot)" to emptyList(),    // D5
                "Chain of Thought (CoT)" to emptyList(),                // D5
                "Tree of Thoughts" to emptyList(),                      // D5
                "RAG (Retrieval Augmented Generation)" to listOf("rag"),
                "Vector Databases (Pinecone/Chroma)" to emptyList(),    // D5
                "ReAct (Reasoning + Acting)" to emptyList(),            // D5
                "AI Agents & Tool Use" to emptyList(),                  // D5
                "Hallucination Mitigation" to emptyList(),              // D5
            ),
            "Fine-Tuning & Optimization" to linkedMapOf(
                "Transfer Learning" to listOf("transfer_learning"),
                "Fine-Tuning (Full)" to emptyList(),                    // D6
                "RLHF (RL with Human Feedback)" to listOf("rlhf"),
                "DPO (Direct Preference Optimization)" to emptyList(),  // D6
                "PEFT (Parameter-Efficient Fine-Tuning)" to emptyList(), // D6
                "LoRA & QLoRA" to emptyList(),                          // D6
                "Quantization (4-bit / 8-bit)" to emptyList(),          // D6
                "Flash Attention" to emptyList(),                       // D6
            ),
            "Beyond Transformers" to linkedMapOf(
                "State Space Models (SSMs)" to emptyList(),             // D6
                "Mamba Architecture" to emptyList(),                    // D6
                "RWKV (RNN with Transformer perf)" to emptyList(),      // D6
                "Long Context Windows (1M+ tokens)" to emptyList(),     // D6
            ),
            "NLP Metrics" to linkedMapOf(
                "Perplexity" to emptyList(),                            // D6
                "WER (Word Error Rate)" to emptyList(),                 // D6
                "BLEU Score (Translation)" to emptyList(),              // D6
                "ROUGE Score (Summarization)" to emptyList(),           // D6
                "METEOR" to emptyList(),                                // D6
                "MMLU (Massive Multitask Benchmark)" to emptyList(),    // D6
            ),
        ),
        // Complete since Track A. The app also ships VDN, QMIX and MADDPG, which the doc never lists.
        "Reinforcement Learning" to linkedMapOf(
            "Core Concepts (The Basics)" to linkedMapOf(
                "Agent & Environment" to listOf("agent_environment"),
                "State, Action, Reward" to listOf("state_action_reward"),
                "The Policy (π)" to listOf("policy"),
                "Value Function (V)" to listOf("value_function"),
                "Q-Function (Q)" to listOf("q_function"),
                "The Horizon & Discount Factor (γ)" to listOf("discount_factor"),
                "Exploration vs Exploitation" to listOf("exploration_exploitation"),
                "Markov Decision Process (MDP)" to listOf("mdp"),
                "Partially Observable MDP (POMDP)" to listOf("pomdp"),
            ),
            "Tabular Methods (Classical)" to linkedMapOf(
                "Bellman Equation" to listOf("bellman_equation"),
                "Dynamic Programming" to listOf("dynamic_programming"),
                "Policy Iteration" to listOf("policy_iteration"),
                "Value Iteration" to listOf("value_iteration"),
                // `monte_carlo_method` in the DSA taxonomy is randomized sampling; RL's is this one.
                "Monte Carlo Methods" to listOf("monte_carlo_rl"),
                "Temporal Difference (TD) Learning" to listOf("td_learning"),
                "SARSA (State-Action-Reward-State-Action)" to listOf("sarsa"),
                "Q-Learning (Off-Policy)" to listOf("q_learning"),
            ),
            "Deep Q-Networks (DQN Family)" to linkedMapOf(
                "Deep Q-Network (DQN)" to listOf("dqn"),
                "Experience Replay" to listOf("experience_replay"),
                "Target Networks" to listOf("target_networks"),
                "Double DQN" to listOf("double_dqn"),
                "Dueling DQN" to listOf("dueling_dqn"),
                "Prioritized Experience Replay (PER)" to listOf("prioritized_replay"),
                "Noisy Nets (For Exploration)" to listOf("noisy_nets"),
                "Distributional RL (C51)" to listOf("c51"),
                "Rainbow DQN (State of the Art)" to listOf("rainbow_dqn"),
            ),
            "Policy Gradient Methods" to linkedMapOf(
                "REINFORCE Algorithm" to listOf("reinforce"),
                "Actor-Critic Architecture" to listOf("actor_critic"),
                "A2C (Advantage Actor-Critic)" to listOf("a2c"),
                "A3C (Asynchronous A2C)" to listOf("a3c"),
                "GAE (Generalized Advantage Estimation)" to listOf("gae"),
                "TRPO (Trust Region Policy Optimization)" to listOf("trpo"),
                "PPO (Proximal Policy Optimization)" to listOf("ppo"),
            ),
            "Continuous Control (Robotics)" to linkedMapOf(
                "Deterministic Policy Gradients (DPG)" to listOf("dpg"),
                "DDPG (Deep DPG)" to listOf("ddpg"),
                "TD3 (Twin Delayed DDPG)" to listOf("td3"),
                "SAC (Soft Actor-Critic)" to listOf("sac"),
                "Maximum Entropy RL" to listOf("max_entropy_rl"),
            ),
            "Model-Based & Planning" to linkedMapOf(
                "Dyna-Q" to listOf("dyna_q"),
                "MCTS (Monte Carlo Tree Search)" to listOf("mcts"),
                "AlphaGo Architecture" to listOf("alphago"),
                "AlphaZero (Self-Play)" to listOf("alphazero"),
                "MuZero (Rules Learned)" to listOf("muzero"),
                "World Models" to listOf("world_models"),
                "Dreamer (V1, V2, V3)" to listOf("dreamer"),
                "MBPO (Model-Based Policy Optimization)" to listOf("mbpo"),
            ),
            "Exploration Strategies" to linkedMapOf(
                "Epsilon-Greedy" to listOf("epsilon_greedy"),
                "Upper Confidence Bound (UCB)" to listOf("ucb"),
                "Thompson Sampling" to listOf("thompson_sampling"),
                "Boltzmann Exploration" to listOf("boltzmann_exploration"),
                "Intrinsic Motivation" to listOf("intrinsic_motivation"),
                "Curiosity-Driven Exploration (ICM)" to listOf("icm"),
                "Random Network Distillation (RND)" to listOf("rnd"),
            ),
            "Multi-Agent Systems (MARL)" to linkedMapOf(
                "Minimax Algorithm" to listOf("minimax"),
                "Independent Q-Learning (IQL)" to listOf("iql"),
                "Self-Play (Competitive)" to listOf("self_play"),
            ),
            "Advanced Frontiers" to linkedMapOf(
                "Imitation Learning (Behavior Cloning)" to listOf("imitation_learning"),
                "Inverse Reinforcement Learning (IRL)" to listOf("irl"),
                "GAIL (Generative Adversarial Imitation)" to listOf("gail"),
                "Offline RL (Batch RL)" to listOf("offline_rl"),
                "CQL (Conservative Q-Learning)" to listOf("cql"),
                "Decision Transformer (RL as Sequence)" to listOf("decision_transformer"),
                "Meta-RL (MAML)" to listOf("meta_rl"),
                "RLHF (Reinforcement Learning from Human Feedback)" to listOf("rlhf"),
            ),
            "Famous Benchmarks" to linkedMapOf(
                "Grid World" to listOf("grid_world"),
                "CartPole (The \"Hello World\")" to listOf("cartpole"),
                "Mountain Car" to listOf("mountain_car"),
                "Atari 2600 (Pong, Breakout)" to listOf("atari"),
                "MuJoCo (Physics Sim)" to listOf("mujoco"),
                "StarCraft II (AlphaStar)" to listOf("starcraft"),
                "Dota 2 (OpenAI Five)" to listOf("dota2"),
            ),
        ),
    )

    // Counted off docs/topics.ai.md bullet by bullet: 12+9+7+11+13+9+11+3+6+9+17+6 (ML 113) +
    // 7+10+12+9+7+11+9+7+11+7+6 (DL 96) + 8+8+7+6+6+6+10+8+8+4+6 (NLP 77) +
    // 9+8+9+7+5+8+7+3+8+7 (RL 71). The phase-9 plan's 357 figure checks out.
    private val expectedEntryCount = 357

    // The ratchet. Move it when a batch lands; a batch that does not move it did not close any doc
    // entry, which is worth noticing.
    private val expectedCoveredCount = 207

    // Doc entries the app answers from the DSA taxonomy rather than an AI section. Deliberate reuse
    // — the alternative is a duplicate page — but small and explicit, so it cannot grow by accident.
    private val allowedDsaIds = setOf("edit_distance")

    private val aiTopicIds: Set<String> =
        (MachineLearningTopics.topics + DeepLearningTopics.topics + NlpTopics.topics +
            ReinforcementLearningTopics.topics).map { it.id }.toSet()

    private val allEntries: List<Pair<String, List<String>>>
        get() = taxonomy.flatMap { (section, headings) ->
            headings.flatMap { (heading, entries) ->
                entries.map { (entry, ids) -> "$section :: $heading :: $entry" to ids }
            }
        }

    @Test
    fun `the map covers every entry the doc lists`() {
        assertEquals(
            "docs/topics.ai.md entry count changed — update the map, not this number",
            expectedEntryCount,
            allEntries.size,
        )
    }

    @Test
    fun `every mapped topic id resolves in the registry`() {
        val broken = allEntries.flatMap { (entry, ids) ->
            ids.filter { TopicRegistry.find(it) == null }.map { "$entry -> $it" }
        }
        assertTrue("Taxonomy entries pointing at topics that do not exist: $broken", broken.isEmpty())
    }

    @Test
    fun `every mapped topic is an AI topic`() {
        val misplaced = allEntries.flatMap { (entry, ids) ->
            ids.filter { it !in aiTopicIds && it !in allowedDsaIds }.map { "$entry -> $it" }
        }
        assertTrue("Taxonomy entries satisfied by non-AI topics: $misplaced", misplaced.isEmpty())
    }

    @Test
    fun `coverage matches the number the phase plan reports`() {
        val covered = allEntries.count { it.second.isNotEmpty() }
        assertEquals(
            "Doc coverage moved. Update expectedCoveredCount and the phase-9 progress table together" +
                " — this number is what that table reports.",
            expectedCoveredCount,
            covered,
        )
    }

    @Test
    fun `the uncovered entries are the phase's remaining work`() {
        // Not an assertion about a number: this is the failure message doing the work. When it
        // fires, the list it prints is the batch backlog, straight from the doc.
        val uncovered = allEntries.filter { it.second.isEmpty() }.map { it.first }
        assertEquals(
            "Uncovered doc entries:\n" + uncovered.joinToString("\n"),
            expectedEntryCount - expectedCoveredCount,
            uncovered.size,
        )
    }
}
