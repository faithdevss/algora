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

private val supervised = MachineLearningCategories.supervised
private val unsupervised = MachineLearningCategories.unsupervised

private val supervisedTopics = listOf(
    topic(
        "linear_regression", "Linear Regression", supervised,
        "Fitting a line to data",
        iconName = "trend", accentColor = 0xFF6366F1, difficulty = Difficulty.BEGINNER,
    ),
    topic(
        "perceptron", "The Perceptron", supervised,
        "The first artificial neuron",
        iconName = "robot", accentColor = 0xFF6366F1, difficulty = Difficulty.BEGINNER,
    ),
    topic("logistic_regression", "Logistic Regression", supervised, "Linear model squashed into a probability.", isPremium = true),
    topic("decision_trees", "Decision Trees", supervised, "Recursive if/else splits that carve up the feature space.", isPremium = true),
    topic("svm", "Support Vector Machines", supervised, "Find the maximum-margin separating hyperplane.", isPremium = true),
    topic("knn", "k-Nearest Neighbors", supervised, "Classify by a majority vote of the closest points.", isPremium = true),
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
    val topics: List<Topic> = supervisedTopics + unsupervisedTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
