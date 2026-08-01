package com.algora.app.feature.machinelearning

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.model.Topic

// Same defaulting convention as DataStructuresTopics: a topic's chrome falls back to its owning
// category unless the mock specifies bespoke icon/color (Linear Regression, The Perceptron).
private fun topic(
    id: String,
    name: String,
    category: Category,
    tagline: String,
    isPremium: Boolean = false,
    iconName: String = category.iconName,
    accentColor: Long = category.accentColor,
    difficulty: Difficulty? = null,
) = Topic(
    id = id,
    name = name,
    categoryId = category.id,
    tagline = tagline,
    description = tagline,
    iconName = iconName,
    accentColor = accentColor,
    isPremium = isPremium,
    difficulty = difficulty,
)

private val regression = MachineLearningCategories.regression
private val classification = MachineLearningCategories.classification
private val bayesian = MachineLearningCategories.bayesian
private val ensemble = MachineLearningCategories.ensemble
private val clustering = MachineLearningCategories.clustering
private val dimReduction = MachineLearningCategories.dimReduction
private val association = MachineLearningCategories.association
private val timeSeries = MachineLearningCategories.timeSeries
private val preprocessing = MachineLearningCategories.preprocessing
private val metrics = MachineLearningCategories.metrics
private val nnFoundations = MachineLearningCategories.nnFoundations
private val rlFundamentals = MachineLearningCategories.rlFundamentals

// Ordered as a reading path: the plain fit, then the two penalties, then the estimators that change
// the loss or the assumptions.
private val regressionTopics = listOf(
    topic(
        "linear_regression", "Linear Regression", regression,
        "Fitting a line to data",
        iconName = "trend", accentColor = 0xFF6366F1, difficulty = Difficulty.BEGINNER,
    ),
    topic("polynomial_regression", "Polynomial Regression", regression, "Same linear machinery, richer basis — and the first place overfitting shows up.", difficulty = Difficulty.BEGINNER),
    topic("ridge_regression", "Ridge Regression (L2)", regression, "Penalize large coefficients to trade a little bias for much less variance.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("lasso_regression", "Lasso Regression (L1)", regression, "The penalty that drives coefficients to exactly zero.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("elasticnet_regression", "ElasticNet Regression", regression, "Mix L1 and L2 when features are both many and correlated.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("stepwise_regression", "Stepwise Regression", regression, "Add features greedily — and why the resulting p-values are not trustworthy.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("robust_regression", "Robust Regression (RANSAC)", regression, "Fit the inliers and ignore the rest, when squared error cannot.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("quantile_regression", "Quantile Regression", regression, "Model a percentile of the response, not its mean.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("bayesian_ridge", "Bayesian Ridge Regression", regression, "Return a distribution over fits, so the model can say where it is unsure.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("poisson_regression", "Poisson Regression", regression, "The right model for counts, where a straight line predicts negatives.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("isotonic_regression", "Isotonic Regression", regression, "A monotone step function with no shape assumed beyond direction.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("lars", "Least Angle Regression (LARS)", regression, "Walk the whole coefficient path, one equiangular step at a time.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// The doc's Classification block, in full: the four that already existed plus the five added here.
private val classificationTopics = listOf(
    topic("logistic_regression", "Logistic Regression", classification, "Linear model squashed into a probability.", isPremium = true),
    topic("knn", "k-Nearest Neighbors", classification, "Classify by a majority vote of the closest points.", isPremium = true),
    topic("decision_trees", "Decision Trees", classification, "Recursive if/else splits that carve up the feature space.", isPremium = true),
    topic("svm", "Support Vector Machines (Linear)", classification, "Find the maximum-margin separating hyperplane.", isPremium = true),
    topic("svm_rbf", "SVM (Radial Basis Function)", classification, "The kernel trick: a curved boundary without ever leaving the inner product.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("nu_svc", "Nu-Support Vector Classification", classification, "Replace C with a knob that actually bounds something.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("lda", "Linear Discriminant Analysis (LDA)", classification, "Model each class as a Gaussian, share one covariance, get a line.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("qda", "Quadratic Discriminant Analysis (QDA)", classification, "Drop the shared-covariance assumption and the boundary curves.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("passive_aggressive", "Passive Aggressive Classifier", classification, "Ignore what you got right; fix what you got wrong, exactly.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// The generic `naive_bayes` entry leads the category as the shared idea; the five variants that
// follow differ only in the likelihood they assume, which is the point worth making.
private val bayesianTopics = listOf(
    topic("naive_bayes", "Naive Bayes", bayesian, "Probabilistic classifier assuming feature independence.", isPremium = true),
    topic("gaussian_nb", "Gaussian Naive Bayes", bayesian, "Continuous features, one mean and variance per feature per class.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("multinomial_nb", "Multinomial Naive Bayes", bayesian, "Word counts, and the text classifier that refuses to die.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("bernoulli_nb", "Bernoulli Naive Bayes", bayesian, "Presence and absence — and absence is evidence too.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("complement_nb", "Complement Naive Bayes", bayesian, "Estimate from every other class, to survive imbalance.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("categorical_nb", "Categorical Naive Bayes", bayesian, "Unordered discrete features, with a table per feature.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("bayesian_networks", "Bayesian Networks", bayesian, "Drop the naive assumption: encode which dependencies actually exist.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("mcmc", "Markov Chain Monte Carlo (MCMC)", bayesian, "Sample a posterior you cannot integrate, by building a chain that visits it.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// Ordered as the two families they belong to: averaging independent models (bagging, forests, extra
// trees, voting, stacking) then correcting sequential errors (boosting), with the three production
// GBM implementations last.
private val ensembleTopics = listOf(
    topic("bagging", "Bagging (Bootstrap Aggregating)", ensemble, "Resample, refit, average — the variance-reduction recipe everything else builds on.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("random_forest", "Random Forests", ensemble, "Bagged decision trees that vote, each seeing a random slice of the data.", isPremium = true),
    topic("extra_trees", "Extra Trees Classifier", ensemble, "Draw the split at random instead of searching for it — worse trees, better forest.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("voting", "Voting Classifiers", ensemble, "Combine independent models by counting votes or averaging confidence.", isPremium = true, difficulty = Difficulty.BEGINNER),
    topic("stacking", "Stacking & Blending", ensemble, "Learn the combination rule, and the leakage trap that ruins it.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("adaboost", "AdaBoost", ensemble, "Re-weight toward what you got wrong, then vote by competence.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("gradient_boosting", "Gradient Boosting Machines (GBM)", ensemble, "Trees added one at a time, each fitting the previous ensemble's errors.", isPremium = true),
    topic("xgboost", "XGBoost (Extreme Gradient Boosting)", ensemble, "Second-order gradients and a regularized objective, written as an optimization.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("lightgbm", "LightGBM", ensemble, "Leaf-wise growth and histogram binning — the speed comes from both.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("catboost", "CatBoost", ensemble, "Oblivious trees, and an encoding of categoricals that does not leak the target.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("isolation_forest", "Isolation Forest (Anomaly Detection)", ensemble, "Anomalies are easy to separate — so count how many random cuts it takes.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
)

// The doc's Data Preprocessing Techniques block in full. Ordered as a pipeline runs rather than as
// the doc lists it: fix the rows first (missing values, outliers), then the columns (encoding,
// scaling), then the class balance, and only then decide which features to keep. Every topic's lab
// scores its rule against a model instead of asserting it, which is why three of them end up
// conditional. `one_hot_encoding` is cross-listed into NLP, where the doc lists it as the baseline
// the word-embedding block improves on.
private val preprocessingTopics = listOf(
    topic("missing_value_imputation", "Missing Value Imputation", preprocessing, "Fill the holes, and measure what filling them did to the column.", difficulty = Difficulty.BEGINNER),
    topic("outlier_detection", "Outlier Detection (IQR/Z)", preprocessing, "The z-score rule, the sample it flags nothing in, and the size it cannot fire at.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("label_encoding", "Label Encoding", preprocessing, "40× the error in a linear model, and exactly free in a tree.", difficulty = Difficulty.BEGINNER),
    topic("one_hot_encoding", "One-Hot Encoding", preprocessing, "A column per level, the false geometry it removes, and the level everyone drops.", difficulty = Difficulty.BEGINNER),
    topic("min_max_normalization", "Min-Max Normalization", preprocessing, "Two features, one distance metric — and income is 99.99% of it.", difficulty = Difficulty.BEGINNER),
    topic("z_score_standardization", "Z-Score Standardization", preprocessing, "The same fix with different failure modes under contamination.", isPremium = true, difficulty = Difficulty.BEGINNER),
    topic("smote", "SMOTE (Oversampling)", preprocessing, "Interpolated minority rows, the precision they cost, and the leak they invite.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("chi_square_selection", "Chi-Square Feature Selection", preprocessing, "Free, univariate, and it ranks noise above an XOR pair.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("rfe", "Recursive Feature Elimination", preprocessing, "Fit, drop the weakest, refit — and inherit your model's blind spots.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
)

// The doc's Model Evaluation Metrics block in full, led by the `model_evaluation` umbrella moved
// over from ml_supervised. Ordered as a reading path: the confusion matrix first because every
// classification metric below is a function of its four cells, then the summaries of it, then the
// two curves, then the probability and agreement corrections, then the regression group, the two
// loss functions and finally the two clustering indices — which are the only ones here that work
// without labels.
private val metricsTopics = listOf(
    topic("model_evaluation", "Model Evaluation", metrics, "Precision, recall, F1, ROC-AUC and the thresholds behind them.", isPremium = true),
    topic("confusion_matrix", "Confusion Matrix", metrics, "Four cells, and the only object here that loses no information.", difficulty = Difficulty.BEGINNER),
    topic("accuracy", "Accuracy", metrics, "0.947 from the model, 0.912 from answering \"no\" every time.", difficulty = Difficulty.BEGINNER),
    topic("precision_recall", "Precision & Recall", metrics, "Perfect precision with 53 misses, or perfect recall with 325 false alarms.", difficulty = Difficulty.BEGINNER),
    topic("f1_score", "F1 Score", metrics, "Why the mean is harmonic, and the threshold that takes 0.569 to 0.776.", isPremium = true, difficulty = Difficulty.BEGINNER),
    topic("roc_curve", "ROC Curve", metrics, "Threshold-free by construction — which is also what it cannot see.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("auc", "AUC Score", metrics, "One number, two definitions, and blind to calibration by design.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("log_loss", "Log Loss (Cross-Entropy)", metrics, "The metric that reads probabilities — and punishes one confident error 13×.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("cohens_kappa", "Cohen's Kappa", metrics, "Accuracy 0.912 with kappa 0.000, on the same predictions.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("mse", "Mean Squared Error (MSE)", metrics, "Four points out of 44 carrying 81% of the error.", difficulty = Difficulty.BEGINNER),
    topic("rmse", "Root Mean Squared Error (RMSE)", metrics, "The same ranking in units you can actually judge.", difficulty = Difficulty.BEGINNER),
    topic("mae", "Mean Absolute Error (MAE)", metrics, "Not a gentler report — a different objective that picks a different line.", isPremium = true, difficulty = Difficulty.BEGINNER),
    topic("r_squared", "R-Squared (R²)", metrics, "Better than predicting the mean, and nothing more than that.", isPremium = true, difficulty = Difficulty.BEGINNER),
    topic("adjusted_r_squared", "Adjusted R²", metrics, "Eight columns of noise: R² rises every time, adjusted R² does not.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("gini_impurity", "Gini Impurity", metrics, "A splitting criterion, not a metric — and not the Gini coefficient.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("hinge_loss", "Hinge Loss", metrics, "Zero past the margin, so 890 of 1,000 examples stop mattering.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("silhouette_score", "Silhouette Score", metrics, "Finds k = 3 on blobs and k = 6 on two rings.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("davies_bouldin", "Davies-Bouldin Index", metrics, "Agrees with silhouette, including when both are wrong.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// The doc's Neural Network Foundations block, from the ML side. `perceptron`, `bias_variance` and
// `regularization` are `ml_supervised`'s last three topics, moved in — `ml_supervised` is now gone.
// Seven more (`mlp` through `autoencoders`) are cross-listed DL topics, browsable from ML mode with
// no duplicate content file, same convention as `perceptron` already being cross-listed into
// `dl_basics`. Only `restricted_boltzmann_machines` and `deep_belief_networks` are genuinely new.
// Ordered as a reading path: the unit, the network, how it trains, what regularizes it, and the two
// unsupervised architectures — RBM then the DBN stacked from it — last.
private val nnFoundationsTopics = listOf(
    topic(
        "perceptron", "The Perceptron", nnFoundations,
        "The first artificial neuron",
        iconName = "robot", accentColor = 0xFF6366F1, difficulty = Difficulty.BEGINNER,
    ),
    topic("mlp", "Multi-Layer Perceptron (MLP)", nnFoundations, "One hidden layer, and the problem a single unit provably cannot solve.", difficulty = Difficulty.BEGINNER),
    topic("backpropagation", "Backpropagation Algorithm", nnFoundations, "The chain rule applied to train every weight in a network.", isPremium = true),
    topic("gradient_descent_variants", "Gradient Descent Variants (SGD, Adam)", nnFoundations, "SGD, Momentum, RMSProp, Adam and friends.", isPremium = true),
    topic("activation_functions", "Activation Functions", nnFoundations, "Non-linearities (ReLU, sigmoid, tanh) that give networks their power.", isPremium = true),
    topic("bias_variance", "Bias-Variance Tradeoff", nnFoundations, "Why underfitting and overfitting pull in opposite directions.", isPremium = true),
    topic("regularization", "Regularization (L1 / L2)", nnFoundations, "Penalize large weights so the model stops memorizing noise.", isPremium = true),
    topic("dropout", "Dropout", nnFoundations, "Randomly silence neurons during training so none becomes indispensable.", isPremium = true),
    topic("batch_normalization", "Batch Normalization", nnFoundations, "Re-centre and re-scale activations so deep stacks stay trainable.", isPremium = true),
    topic("autoencoders", "Autoencoders", nnFoundations, "Encode-then-reconstruct networks for compression and denoising.", isPremium = true),
    topic("restricted_boltzmann_machines", "Restricted Boltzmann Machines", nnFoundations, "No visible-visible or hidden-hidden links — just one weight matrix, trained by CD-1.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("deep_belief_networks", "Deep Belief Networks", nnFoundations, "Stack RBMs, train greedily, and the top layer separates classes before any label is used.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// The doc's RL Fundamentals block, from the ML side. Five cross-listed RL topics plus one new one —
// `multi_armed_bandit`, the problem itself, framed against the strategies (its siblings here and in
// RL mode) that solve it.
private val rlFundamentalsTopics = listOf(
    topic("multi_armed_bandit", "Multi-Armed Bandit", rlFundamentals, "Four arms, hidden win rates — and what pure random exploration alone gets you.", difficulty = Difficulty.BEGINNER),
    topic("mdp", "Markov Decision Process (MDP)", rlFundamentals, "State, action, reward and a transition — the framework every RL algorithm assumes.", isPremium = true),
    topic("q_learning", "Q-Learning (Off-Policy)", rlFundamentals, "Learn the value of every action without ever following the policy being learned.", isPremium = true),
    topic("sarsa", "SARSA (On-Policy)", rlFundamentals, "The same update, following the policy actually being run — including its exploration.", isPremium = true),
    topic("thompson_sampling", "Thompson Sampling", rlFundamentals, "Sample from a belief distribution per arm, rather than committing to one estimate.", isPremium = true),
    topic("ucb", "Upper Confidence Bound (UCB)", rlFundamentals, "A confidence bonus that shrinks with evidence — no randomness needed at all.", isPremium = true),
)

// The doc's Clustering block in full. Ordered by family: centroid, hierarchical, density, then the
// three that do not fit any of those.
private val clusteringTopics = listOf(
    topic("kmeans", "K-Means Clustering", clustering, "Partition points into k clusters around moving centroids.", isPremium = true),
    topic("k_medians", "K-Medians", clustering, "Swap the mean for the median and the squared error for absolute.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("k_modes", "K-Modes", clustering, "The same loop for categorical data, which has no mean at all.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("hierarchical_clustering", "Hierarchical (Agglomerative)", clustering, "Build a tree of nested clusters by merging the closest pair.", isPremium = true),
    topic("hierarchical_divisive", "Hierarchical (Divisive)", clustering, "The same dendrogram built downward, splitting instead of merging.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("dbscan", "DBSCAN", clustering, "Density-based clustering that finds arbitrary shapes and noise.", isPremium = true),
    topic("hdbscan", "HDBSCAN", clustering, "Every eps at once, keeping the clusters that persist across them.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("optics", "OPTICS", clustering, "An ordering and a reachability plot instead of one fixed eps.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("mean_shift", "Mean Shift Clustering", clustering, "Climb the density gradient; the peaks are the clusters.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("birch", "BIRCH", clustering, "One streaming pass that summarizes rather than stores.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("affinity_propagation", "Affinity Propagation", clustering, "Points message each other until exemplars emerge.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("spectral_clustering", "Spectral Clustering", clustering, "Cluster by connectivity, using the graph Laplacian's eigenvectors.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("gmm", "Gaussian Mixture Models (GMM)", clustering, "Soft assignments and full covariances — k-means is its special case.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// The doc's Dimensionality Reduction block in full. Ordered as the two families it really contains:
// the linear factorizations first (PCA and the three things that are PCA seen from another angle),
// then the manifold methods, which answer a different question and are not interchangeable with them.
// `svd` is free — it is the linear algebra the first four topics are all special cases of.
private val dimReductionTopics = listOf(
    topic("pca", "Principal Component Analysis (PCA)", dimReduction, "Project data onto its directions of greatest variance.", isPremium = true),
    topic("svd", "Singular Value Decomposition (SVD)", dimReduction, "The factorization underneath PCA — and the theorem that says truncating it is optimal.", difficulty = Difficulty.INTERMEDIATE),
    topic("kernel_pca", "Kernel PCA", dimReduction, "PCA in a feature space you never have to construct.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("incremental_pca", "Incremental PCA", dimReduction, "Fit the same components a batch at a time, in memory that does not grow with n.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("ica", "Independent Component Analysis (ICA)", dimReduction, "Separate mixed signals by looking for non-Gaussianity, not variance.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("factor_analysis", "Factor Analysis", dimReduction, "Model common variance and admit that the rest is noise.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("tsne", "t-SNE", dimReduction, "A neighbourhood-preserving map — and everything in it you must not read.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("umap", "UMAP", dimReduction, "A fuzzy k-NN graph laid out by force, keeping more of the global picture.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("lle", "Locally Linear Embedding (LLE)", dimReduction, "Every patch is flat: rebuild each point from its neighbours, then keep the weights.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// Three algorithms, one answer. They are ordered by what each one gives up to be faster than the
// one before it: Apriori scans per level, Eclat trades memory for the scans, FP-Growth trades a tree
// for the candidates.
private val associationTopics = listOf(
    topic("apriori", "Apriori Algorithm", association, "Generate, prune, count — and the property that makes the pruning sound.", difficulty = Difficulty.BEGINNER),
    topic("eclat", "Eclat Algorithm", association, "Store the database by column and support becomes a set intersection.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("fp_growth", "FP-Growth Algorithm", association, "Compress the database into a tree, then mine it with no candidates at all.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// Ordered as the two families: smoothing the past, then modelling it. All six run on the same series
// so the comparison in each lab is against the others rather than against a friendly dataset.
private val timeSeriesTopics = listOf(
    topic("moving_average", "Moving Average (MA)", timeSeries, "The simplest smoother, and the lag it costs you.", difficulty = Difficulty.BEGINNER),
    topic("exponential_smoothing", "Exponential Smoothing (Holt-Winters)", timeSeries, "Level, trend and season, each an exponentially-weighted update.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("autoregression", "Autoregression (AR)", timeSeries, "Regress the series on its own past — and find out what that cannot reach.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("arima", "ARIMA", timeSeries, "Differencing for stationarity, lags for memory, past errors for the rest.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("sarima", "SARIMA (Seasonal)", timeSeries, "One subtraction at lag m, and the annual cycle stops being the model's problem.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("prophet", "Prophet (by Meta)", timeSeries, "Trend plus seasonality plus holidays, fitted as a curve-fitting problem on purpose.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
)

object MachineLearningTopics {
    val topics: List<Topic> = regressionTopics + classificationTopics + bayesianTopics + ensembleTopics + clusteringTopics + dimReductionTopics + associationTopics + timeSeriesTopics + preprocessingTopics + metricsTopics + nnFoundationsTopics + rlFundamentalsTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
