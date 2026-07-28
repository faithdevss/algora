package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val randomForestContent = TopicContent(
    topicId = "random_forest",
    whatIsIt = listOf(
        "A random forest trains many decision trees on different random views of the data and averages their predictions — majority vote for classification, mean for regression.",
        "Two sources of randomness make the trees disagree in useful ways: each tree sees a bootstrap resample of the rows, and each split considers only a random subset of the features. Individually the trees overfit; because their errors are decorrelated, averaging cancels much of that variance.",
    ),
    steps = listOf(
        StepCard(1, "Bootstrap the Rows", "Sample n rows with replacement — about 37% of the data is left out of each tree.", 0xFF6366F1),
        StepCard(2, "Grow a Deep Tree", "Split until the leaves are nearly pure; no pruning is needed because averaging handles the variance.", 0xFF3B82F6),
        StepCard(3, "Randomize the Split Features", "At each node consider only √p features (classification) — this is what decorrelates the trees.", 0xFFF59E0B),
        StepCard(4, "Repeat for B Trees", "Every tree is trained independently, so the whole forest is embarrassingly parallel.", 0xFF10B981),
        StepCard(5, "Aggregate", "Vote or average across all B trees to produce the final prediction.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Prediction", "ŷ = (1/B) Σ_b T_b(x)   |   ŷ = mode{ T_b(x) }", "Regression average; classification vote."),
        FormulaEntry("Variance of the average", "ρσ² + (1−ρ)σ²/B", "Correlation ρ between trees sets the floor — hence feature subsampling."),
        FormulaEntry("Features per split", "m ≈ √p classification, p/3 regression", "The standard defaults."),
        FormulaEntry("Out-of-bag error", "error over the ~37% of rows each tree never saw", "A free validation estimate, no held-out split needed."),
    ),
    notationKey = listOf(
        NotationEntry("B", "number of trees in the forest"),
        NotationEntry("p, m", "total features, and features considered per split"),
        NotationEntry("ρ", "average correlation between tree predictions"),
        NotationEntry("OOB", "out-of-bag — rows excluded from a given tree's bootstrap sample"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Bagging + feature subsampling, in outline",
            accentColor = 0xFF6366F1,
            code = """
                class RandomForest(
                    private val treeCount: Int = 100,
                    private val featuresPerSplit: Int? = null,   // null -> sqrt(p)
                ) {
                    private val trees = mutableListOf<DecisionTree>()

                    fun fit(x: Array<DoubleArray>, y: IntArray) {
                        val n = x.size
                        val p = x[0].size
                        val m = featuresPerSplit ?: Math.sqrt(p.toDouble()).toInt().coerceAtLeast(1)

                        repeat(treeCount) {
                            // Bootstrap: n draws WITH replacement, so rows repeat and some are omitted.
                            val rows = IntArray(n) { (0 until n).random() }
                            val tree = DecisionTree(featureSubsetSize = m)
                            tree.fit(rows.map { x[it] }.toTypedArray(), rows.map { y[it] }.toIntArray())
                            trees += tree
                        }
                    }

                    // Majority vote across the ensemble.
                    fun predict(sample: DoubleArray): Int =
                        trees.map { it.predict(sample) }
                            .groupingBy { it }
                            .eachCount()
                            .maxBy { it.value }
                            .key
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF6366F1, "Tabular Baselines", "On mixed numeric/categorical tables a forest is the model to beat before anything fancier is justified."),
        ApplicationCard("globe", 0xFF3B82F6, "Risk & Fraud Scoring", "Robust to outliers and unscaled features, with feature importances that analysts can inspect."),
        ApplicationCard("image", 0xFF10B981, "Sensor & Pose Classification", "Kinect body-part recognition famously ran on a random forest in real time."),
    ),
    takeaways = listOf(
        "Averaging decorrelated high-variance trees cuts variance without adding bias.",
        "Feature subsampling — not bootstrapping alone — is what keeps the trees from agreeing.",
        "Out-of-bag error gives validation for free.",
        "Forests resist overfitting as trees are added; boosting, by contrast, can overfit with too many rounds.",
    ),
    crossLinks = listOf(
        CrossLink("decision_trees", "Decision Trees"),
        CrossLink("gradient_boosting", "Gradient Boosting"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
    ),
)
