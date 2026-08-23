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

internal val dbscanContent = TopicContent(
    topicId = "dbscan",
    figure = Figure(
        caption = "Ten points at ε = 0.22 with minPts = 4, counting the point itself. Seven have " +
            "four neighbours inside the radius and are core; two have three and two, so they are " +
            "border points — not dense enough to grow a cluster, but close enough to a core point to " +
            "join one. The tenth has nothing within 0.22 of it and is noise, which is a label DBSCAN " +
            "produces rather than an error state. Note what fixes the two clusters: not a count, not " +
            "a shape, only which points can be chained together through core points.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("c", 0.18f, 0.72f, FigureTone.Primary),
                FigureGraphNode("c", 0.30f, 0.78f, FigureTone.Primary),
                FigureGraphNode("c", 0.26f, 0.60f, FigureTone.Primary),
                FigureGraphNode("b", 0.14f, 0.86f, FigureTone.Accent),
                FigureGraphNode("b", 0.46f, 0.62f, FigureTone.Accent),
                FigureGraphNode("c", 0.70f, 0.34f, FigureTone.Primary),
                FigureGraphNode("c", 0.82f, 0.40f, FigureTone.Primary),
                FigureGraphNode("c", 0.76f, 0.22f, FigureTone.Primary),
                FigureGraphNode("c", 0.88f, 0.28f, FigureTone.Primary),
                FigureGraphNode("n", 0.50f, 0.10f, FigureTone.Warn),
            ),
            edges = listOf(
                FigureEdge(0, 1, tone = FigureTone.Primary),
                FigureEdge(0, 2, tone = FigureTone.Primary),
                FigureEdge(1, 2, tone = FigureTone.Primary),
                FigureEdge(0, 3, tone = FigureTone.Accent),
                FigureEdge(1, 3, tone = FigureTone.Accent),
                FigureEdge(2, 4, tone = FigureTone.Accent),
                FigureEdge(5, 6, tone = FigureTone.Primary),
                FigureEdge(5, 7, tone = FigureTone.Primary),
                FigureEdge(5, 8, tone = FigureTone.Primary),
                FigureEdge(6, 7, tone = FigureTone.Primary),
                FigureEdge(6, 8, tone = FigureTone.Primary),
                FigureEdge(7, 8, tone = FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "DBSCAN clusters points by density: it groups together points packed closely and labels points in sparse regions as noise.",
        "Unlike K-Means it needs no preset cluster count, finds arbitrarily shaped clusters, and explicitly identifies outliers.",
    ),
    steps = listOf(
        StepCard(1, "Define a Neighborhood", "Two parameters: ε, the radius, and minPts, the density threshold.", 0xFF818CF8),
        StepCard(2, "Find Core Points", "A point with ≥ minPts neighbors within ε is a core point.", 0xFF60A5FA),
        StepCard(3, "Grow Clusters", "Connect core points and their neighbors into density-reachable clusters.", 0xFF10B981),
        StepCard(4, "Mark Noise", "Points not reachable from any core point are labeled outliers.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Core point", "|N_ε(p)| ≥ minPts", "Enough neighbors within radius ε."),
        FormulaEntry("Density-reachable", "chain of core points", "How a cluster expands outward."),
        FormulaEntry("Complexity", "O(n log n)", "With a spatial index for neighbor queries."),
    ),
    notationKey = listOf(
        NotationEntry("ε (eps)", "neighborhood radius"),
        NotationEntry("minPts", "minimum neighbors for a core point"),
        NotationEntry("noise", "points in no cluster (outliers)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "DBSCAN (scikit-learn)",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.cluster import DBSCAN

                db = DBSCAN(eps=0.5, min_samples=5)
                labels = db.fit_predict(X)
                # label -1 marks noise/outliers; no n_clusters needed.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("map", 0xFF818CF8, "Geospatial Clustering", "Grouping GPS points into places of interest, ignoring stray readings."),
        ApplicationCard("target", 0xFF60A5FA, "Anomaly Detection", "Its noise label naturally flags fraud, defects, or outliers."),
        ApplicationCard("image", 0xFF10B981, "Arbitrary-Shape Clusters", "It finds elongated or nested clusters that K-Means can't separate."),
    ),
    takeaways = listOf(
        "DBSCAN clusters by density, needing ε and minPts instead of a cluster count.",
        "It finds arbitrarily shaped clusters and labels sparse points as noise.",
        "It struggles when clusters have very different densities, since ε is global.",
        "Choose it over K-Means for irregular shapes and built-in outlier detection.",
    ),
    crossLinks = listOf(
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("knn", "k-Nearest Neighbors"),
    ),
)
