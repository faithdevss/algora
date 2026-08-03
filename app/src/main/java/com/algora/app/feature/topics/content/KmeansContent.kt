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

internal val kmeansContent = TopicContent(
    topicId = "kmeans",
    figure = Figure(
        caption = "Every point belongs to exactly one centroid, and every centroid is the mean of the " +
            "points that chose it — the two halves define each other, which is why the algorithm just " +
            "alternates between them until nothing moves. The point in the middle is the honest part: " +
            "it belongs to whichever centroid is marginally nearer, so its cluster can flip between two " +
            "runs that differ only in where the centroids started.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("c₁", 0.22f, 0.28f, FigureTone.Accent),
                FigureGraphNode("x", 0.08f, 0.12f, FigureTone.Primary),
                FigureGraphNode("x", 0.34f, 0.08f, FigureTone.Primary),
                FigureGraphNode("x", 0.10f, 0.52f, FigureTone.Primary),
                FigureGraphNode("c₂", 0.78f, 0.68f, FigureTone.Accent),
                FigureGraphNode("x", 0.92f, 0.48f),
                FigureGraphNode("x", 0.64f, 0.90f),
                FigureGraphNode("x", 0.94f, 0.88f),
                FigureGraphNode("?", 0.50f, 0.46f, FigureTone.Warn),
            ),
            edges = listOf(
                FigureEdge(1, 0, directed = true, tone = FigureTone.Primary),
                FigureEdge(2, 0, directed = true, tone = FigureTone.Primary),
                FigureEdge(3, 0, directed = true, tone = FigureTone.Primary),
                FigureEdge(5, 4, directed = true),
                FigureEdge(6, 4, directed = true),
                FigureEdge(7, 4, directed = true),
                FigureEdge(8, 0, directed = true, tone = FigureTone.Warn),
                FigureEdge(8, 4, directed = true, tone = FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "K-Means is an unsupervised clustering algorithm that partitions data into k groups, each represented by its centroid — the mean of its members.",
        "It alternates between assigning points to the nearest centroid and recomputing centroids, until the assignments stop changing.",
    ),
    steps = listOf(
        StepCard(1, "Initialize Centroids", "Pick k starting centers (k-means++ spreads them out for better results).", 0xFF818CF8),
        StepCard(2, "Assign", "Attach each point to its nearest centroid.", 0xFF60A5FA),
        StepCard(3, "Update", "Move each centroid to the mean of its assigned points.", 0xFF10B981),
        StepCard(4, "Repeat to Convergence", "Alternate assign/update until membership stabilizes.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "min Σ Σ ‖x − μ_c‖²", "Total within-cluster squared distance (inertia)."),
        FormulaEntry("Centroid", "μ_c = mean of cluster c", "The update step's new center."),
        FormulaEntry("Iteration cost", "O(n·k·d)", "n points, k clusters, d dimensions per pass."),
    ),
    notationKey = listOf(
        NotationEntry("k", "number of clusters (chosen in advance)"),
        NotationEntry("μ_c", "centroid of cluster c"),
        NotationEntry("inertia", "within-cluster sum of squared distances"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "K-Means (scikit-learn)",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.cluster import KMeans

                km = KMeans(n_clusters=3, init="k-means++", n_init=10)
                labels = km.fit_predict(X)

                print(km.cluster_centers_)   # the k centroids
                print(km.inertia_)           # total within-cluster distance
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("users", 0xFF818CF8, "Customer Segmentation", "Grouping users by behavior for targeted marketing is K-Means' flagship use."),
        ApplicationCard("image", 0xFF60A5FA, "Color Quantization", "Reducing an image to k representative colors clusters its pixels."),
        ApplicationCard("chart", 0xFF10B981, "Vector Quantization", "Compressing feature vectors to their nearest centroid for indexing and codebooks."),
    ),
    takeaways = listOf(
        "K-Means alternates assign-to-nearest and recompute-mean until convergence.",
        "You must choose k up front; the elbow or silhouette method helps pick it.",
        "It assumes roughly spherical, equal-size clusters and is sensitive to initialization.",
        "k-means++ initialization and multiple restarts avoid poor local minima.",
    ),
    crossLinks = listOf(
        CrossLink("knn", "k-Nearest Neighbors"),
        CrossLink("dbscan", "DBSCAN"),
    ),
)
