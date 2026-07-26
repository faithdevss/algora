package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val knnContent = TopicContent(
    topicId = "knn",
    whatIsIt = listOf(
        "k-Nearest Neighbors classifies a new point by a majority vote of its k closest training examples — no model is trained, the data is the model.",
        "It's the archetypal lazy learner: all the work happens at query time, computing distances to stored points.",
    ),
    steps = listOf(
        StepCard(1, "Store the Training Set", "Keep every labeled example; there's no fitting phase.", 0xFF818CF8),
        StepCard(2, "Measure Distances", "For a query, compute its distance to all stored points (Euclidean, Manhattan, cosine).", 0xFF60A5FA),
        StepCard(3, "Take the k Nearest", "Select the k closest neighbors.", 0xFF10B981),
        StepCard(4, "Vote or Average", "Classify by majority label (or predict the mean for regression).", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Euclidean distance", "√Σ(xᵢ − x′ᵢ)²", "Straight-line distance between two points."),
        FormulaEntry("Query cost", "O(n·d)", "Distance to all n points in d dimensions."),
        FormulaEntry("Choosing k", "odd, ~√n", "Small k overfits; large k oversmooths."),
    ),
    notationKey = listOf(
        NotationEntry("k", "number of neighbors that vote"),
        NotationEntry("n, d", "training size and feature count"),
        NotationEntry("lazy learner", "no training; work deferred to query time"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "k-NN (scikit-learn)",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.neighbors import KNeighborsClassifier

                clf = KNeighborsClassifier(n_neighbors=5)
                clf.fit(X_train, y_train)      # just stores the data

                preds = clf.predict(X_test)    # distance computation happens here
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF818CF8, "Recommendation", "Suggesting items liked by the most similar users is neighbor voting in disguise."),
        ApplicationCard("image", 0xFF60A5FA, "Image & Pattern Matching", "Nearest-neighbor search over feature vectors powers reverse image and similarity lookup."),
        ApplicationCard("chart", 0xFF10B981, "Baseline Classifier", "Its simplicity makes it the go-to sanity-check baseline for a new dataset."),
    ),
    takeaways = listOf(
        "k-NN predicts from the k closest stored examples — no training, all work at query time.",
        "Distance metric and feature scaling dominate its accuracy.",
        "Query cost is O(n·d); spatial indexes like k-d trees speed it up in low dimensions.",
        "It suffers from the curse of dimensionality as feature counts grow.",
    ),
    crossLinks = listOf(
        CrossLink("kd_tree", "K-D Tree / Quad Tree / Octree"),
        CrossLink("kmeans", "K-Means Clustering"),
    ),
)
