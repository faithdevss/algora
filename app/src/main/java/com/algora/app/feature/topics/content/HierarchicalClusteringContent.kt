package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val hierarchicalClusteringContent = TopicContent(
    topicId = "hierarchical_clustering",
    whatIsIt = listOf(
        "Hierarchical clustering builds a tree of nested clusters (a dendrogram) instead of a single flat partition, so you can cut it at any level to get the number of clusters you want.",
        "The common agglomerative form starts with every point as its own cluster and repeatedly merges the two closest clusters.",
    ),
    steps = listOf(
        StepCard(1, "Start with Singletons", "Each data point begins as its own cluster.", 0xFF818CF8),
        StepCard(2, "Merge the Closest Pair", "Find and merge the two nearest clusters by a linkage rule.", 0xFF60A5FA),
        StepCard(3, "Repeat", "Keep merging until a single cluster remains, recording each merge.", 0xFF10B981),
        StepCard(4, "Cut the Dendrogram", "Slice the merge tree at a chosen height to read off the clusters.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Single linkage", "min distance between members", "Merges by the closest pair of points."),
        FormulaEntry("Complete linkage", "max distance between members", "Merges by the farthest pair — tighter clusters."),
        FormulaEntry("Ward linkage", "min variance increase", "Merges to minimize within-cluster spread."),
    ),
    notationKey = listOf(
        NotationEntry("dendrogram", "tree recording the sequence of merges"),
        NotationEntry("linkage", "rule for the distance between two clusters"),
        NotationEntry("cut height", "threshold that fixes the cluster count"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Agglomerative clustering (scikit-learn)",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.cluster import AgglomerativeClustering

                model = AgglomerativeClustering(n_clusters=3, linkage="ward")
                labels = model.fit_predict(X)
                # Or omit n_clusters and cut a scipy dendrogram at a distance threshold.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFF818CF8, "Phylogenetics", "Building evolutionary trees from genetic distance is hierarchical clustering."),
        ApplicationCard("chart", 0xFF60A5FA, "Exploratory Analysis", "The dendrogram reveals structure and natural cluster counts without fixing k first."),
        ApplicationCard("users", 0xFF10B981, "Taxonomy & Grouping", "Organizing documents or products into nested categories."),
    ),
    takeaways = listOf(
        "Hierarchical clustering produces a full merge tree, not a single partition.",
        "You choose clusters after the fact by cutting the dendrogram at any height.",
        "The linkage rule (single/complete/average/Ward) strongly shapes the clusters.",
        "It needs no preset k but costs O(n²)–O(n³), limiting it to smaller datasets.",
    ),
    crossLinks = listOf(
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("tree", "Tree"),
    ),
)
