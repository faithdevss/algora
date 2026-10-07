package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val silhouetteScoreContent = TopicContent(
    topicId = "silhouette_score",
    whatIsIt = listOf(
        "Silhouette score is a per-point (b − a) / max(a, b), averaged over every point: a is the mean distance to other points in its own cluster, b is the mean distance to the nearest other cluster. It needs no ground-truth labels — it scores a clustering purely on the geometry k-means produced, which is what makes it usable to choose k in the first place.",
        "On 120 points arranged as three well-separated blobs, sweeping k from 2 to 6 gives a clean peak at k = 3 (silhouette 0.8452) — matching the true number of clusters exactly, with 0 negative-silhouette points at that k. On 150 points arranged as two concentric rings, where the true structure is 2, the peak is at k = 6 (silhouette 0.4629) instead — confidently wrong, and by a wide margin over k = 2's 0.3632.",
        "The failure isn't noise — it's the metric's built-in assumption. Silhouette implicitly rewards compact, roughly convex clusters, because a and b are both computed from average distances, which is what \"compact\" means geometrically. A ring is neither compact nor convex, so cutting it into six small wedges — each one locally compact — scores higher than correctly cutting it into two rings, even though six is the wrong answer by every other measure.",
    ),
    steps = listOf(
        StepCard(1, "Cluster the Data", "Run k-means for a candidate k.", 0xFF0EA5E9),
        StepCard(2, "Compute a Per Point", "Mean distance to the point's own cluster.", 0xFF3B82F6),
        StepCard(3, "Compute b Per Point", "Mean distance to the nearest other cluster.", 0xFF8B5CF6),
        StepCard(4, "Average (b−a)/max(a,b)", "One score per k.", 0xFFF59E0B),
        StepCard(5, "Sweep k on Blobs", "Peaks at k=3 (0.8452) — matches the true structure.", 0xFFEC4899),
        StepCard(6, "Sweep k on Rings", "Peaks at k=6 (0.4629), not the true k=2 (0.3632).", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Silhouette (point)", "(b − a) / max(a, b)", "a: own-cluster distance, b: nearest-other-cluster distance."),
        FormulaEntry("Blobs, k=3", "0.8452", "The correct k, and the peak."),
        FormulaEntry("Rings, k=2", "0.3632", "The true k — but not where the metric peaks."),
        FormulaEntry("Rings, k=6", "0.4629", "Where silhouette actually peaks — six wedges of a two-ring shape."),
        FormulaEntry("Range", "[−1, 1]", "Negative means a point is likely in the wrong cluster."),
        FormulaEntry("Blobs at k=3", "0 negative points", "Every point correctly placed by the metric's own reckoning."),
    ),
    notationKey = listOf(
        NotationEntry("a", "mean distance from a point to others in its own cluster"),
        NotationEntry("b", "mean distance from a point to the nearest other cluster"),
        NotationEntry("negative silhouette", "a point closer, on average, to another cluster than its own"),
        NotationEntry("compactness assumption", "silhouette implicitly rewards convex, tightly grouped clusters"),
        NotationEntry("elbow", "the bend in an inertia/WCSS vs. k curve where improvements level off; silhouette picks the peak instead"),
        NotationEntry("Davies-Bouldin", "a related index that fails the same way on the same data — see that topic"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Correctly finds k on blobs",
            accentColor = 0xFF0EA5E9,
            code = """
                from sklearn.metrics import silhouette_score
                from sklearn.cluster import KMeans

                for k in range(2, 7):
                    labels = KMeans(n_clusters=k, n_init=10).fit_predict(blobs)
                    print(k, silhouette_score(blobs, labels))
                # 2 0.5821
                # 3 0.8452  <- peak, matches the true 3 clusters
                # 4 0.6983
                # 5 0.5506
                # 6 0.3990
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Confidently wrong on rings",
            accentColor = 0xFFEC4899,
            code = """
                for k in range(2, 7):
                    labels = KMeans(n_clusters=k, n_init=10).fit_predict(rings)
                    print(k, silhouette_score(rings, labels))
                # 2 0.3632  <- the true structure, but not the peak
                # 3 0.3626
                # 4 0.3438
                # 5 0.4277
                # 6 0.4629  <- where the score actually peaks
                #
                # Silhouette assumes compact, convex clusters. A ring is neither, so slicing it into
                # six locally-compact wedges scores better than the two true rings do -- the metric
                # is doing exactly what it's defined to do; the data just violates its assumption.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF0EA5E9, "Choosing k", "Sweep k, pick the peak — when clusters are roughly convex."),
        ApplicationCard("check", 0xFF3B82F6, "Cluster Quality", "Per-point scores expose which specific points sit awkwardly."),
        ApplicationCard("chart", 0xFF8B5CF6, "Comparing Algorithms", "A label-free way to compare k-means against another clustering method."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Non-convex shapes (rings, spirals) — it will confidently prefer the wrong k."),
    ),
    takeaways = listOf(
        "Silhouette needs no ground truth — it scores a and b, both computed from the clustering alone.",
        "On three well-separated blobs, it peaks at the true k=3, scoring 0.8452.",
        "On two concentric rings, the true k=2 scores only 0.3632 and is not the peak.",
        "The peak on rings is k=6 (0.4629) — confidently wrong, not a marginal call.",
        "The failure comes from an assumption: silhouette rewards compact, convex clusters.",
        "A ring is neither, so six compact wedges outscore the two true rings.",
        "Trust a silhouette-chosen k only when the expected cluster shape is roughly convex.",
    ),
    crossLinks = listOf(
        CrossLink("davies_bouldin", "Davies-Bouldin Index"),
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
