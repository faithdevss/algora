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

private val supervisedTopics = listOf(
    topic(
        "perceptron", "The Perceptron", supervised,
        "The first artificial neuron",
        iconName = "robot", accentColor = 0xFF6366F1, difficulty = Difficulty.BEGINNER,
    ),
    topic("naive_bayes", "Naive Bayes", supervised, "Probabilistic classifier assuming feature independence.", isPremium = true),
    topic("random_forest", "Random Forest", supervised, "Bagged decision trees that vote, each seeing a random slice of the data.", isPremium = true),
    topic("gradient_boosting", "Gradient Boosting", supervised, "Trees added one at a time, each fitting the previous ensemble's errors.", isPremium = true),
    topic("bias_variance", "Bias-Variance Tradeoff", supervised, "Why underfitting and overfitting pull in opposite directions.", isPremium = true),
    topic("regularization", "Regularization (L1 / L2)", supervised, "Penalize large weights so the model stops memorizing noise.", isPremium = true),
    topic("model_evaluation", "Model Evaluation", supervised, "Precision, recall, F1, ROC-AUC and the thresholds behind them.", isPremium = true),
)

private val unsupervisedTopics = listOf(
    topic("kmeans", "K-Means Clustering", unsupervised, "Partition points into k clusters around moving centroids.", isPremium = true),
    topic("hierarchical_clustering", "Hierarchical Clustering", unsupervised, "Build a tree of nested clusters by merging or splitting.", isPremium = true),
    topic("pca", "PCA", unsupervised, "Project data onto its directions of greatest variance.", isPremium = true),
    topic("dbscan", "DBSCAN", unsupervised, "Density-based clustering that finds arbitrary shapes and noise.", isPremium = true),
)

object MachineLearningTopics {
    val topics: List<Topic> = regressionTopics + classificationTopics + supervisedTopics + unsupervisedTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
