package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dbscanContent = TopicContent(
    topicId = "dbscan",
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
