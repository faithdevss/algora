package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val knnContent = TopicContent(
    topicId = "knn",
    figure = Figure(
        caption = "One query, one fixed dataset, and the answer flips on k alone. The five nearest " +
            "points sit at distances 0.11, 0.13, 0.18, 0.25 and 0.25, and their labels arrive in the " +
            "order B, B, A, A, A — so k = 3 votes B two to one and k = 5 votes A three to two. " +
            "Nothing was trained between the two answers; k is not a tuning detail on top of the " +
            "model, it is the model. The two unlinked points are outside both neighbourhoods and " +
            "have no say at all.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("?", 0.50f, 0.52f, FigureTone.Accent),
                FigureGraphNode("B", 0.58f, 0.60f, FigureTone.Primary),
                FigureGraphNode("B", 0.42f, 0.62f, FigureTone.Primary),
                FigureGraphNode("A", 0.50f, 0.34f, FigureTone.Warn),
                FigureGraphNode("A", 0.26f, 0.60f, FigureTone.Warn),
                FigureGraphNode("A", 0.74f, 0.44f, FigureTone.Warn),
                FigureGraphNode("B", 0.30f, 0.24f, FigureTone.Muted),
                FigureGraphNode("A", 0.78f, 0.76f, FigureTone.Muted),
            ),
            edges = listOf(
                FigureEdge(0, 1, "0.11", tone = FigureTone.Primary),
                FigureEdge(0, 2, "0.13", tone = FigureTone.Primary),
                FigureEdge(0, 3, "0.18", tone = FigureTone.Warn),
                FigureEdge(0, 4, "0.25", tone = FigureTone.Warn),
                FigureEdge(0, 5, "0.25", tone = FigureTone.Warn),
            ),
        ),
    ),
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
