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
private val supervised = MachineLearningCategories.supervised
private val unsupervised = MachineLearningCategories.unsupervised

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

private val supervisedTopics = listOf(
    topic(
        "perceptron", "The Perceptron", supervised,
        "The first artificial neuron",
        iconName = "robot", accentColor = 0xFF6366F1, difficulty = Difficulty.BEGINNER,
    ),
    topic("bias_variance", "Bias-Variance Tradeoff", supervised, "Why underfitting and overfitting pull in opposite directions.", isPremium = true),
    topic("regularization", "Regularization (L1 / L2)", supervised, "Penalize large weights so the model stops memorizing noise.", isPremium = true),
    topic("model_evaluation", "Model Evaluation", supervised, "Precision, recall, F1, ROC-AUC and the thresholds behind them.", isPremium = true),
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

private val unsupervisedTopics = listOf(
    topic("pca", "PCA", unsupervised, "Project data onto its directions of greatest variance.", isPremium = true),
)

object MachineLearningTopics {
    val topics: List<Topic> = regressionTopics + classificationTopics + bayesianTopics + ensembleTopics + clusteringTopics + supervisedTopics + unsupervisedTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
