package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val daviesBouldinContent = TopicContent(
    topicId = "davies_bouldin",
    figure = Figure(
        caption = "Davies-Bouldin swept over k = 2..6 with the lab's k-means, on its two datasets — " +
            "lower is better, so read this plot for its minimum. On 66 points in three blobs the " +
            "curve bottoms out at the true k = 3, 0.267, and rises on both sides. On 140 points in " +
            "two concentric rings it does the opposite of what the data needs: the true k = 2 is " +
            "the worst score of the five at 1.085, and the curve falls almost all the way to its " +
            "minimum at k = 6, 0.654. Each ring's centroid sits in the empty middle, so its spread " +
            "is large and the two centroids nearly coincide — the worst possible ratio — while six " +
            "arcs each get a centroid near their own points. Silhouette makes the same two calls on " +
            "the same data, right and then wrong, which is the point: two indices that share the " +
            "compact-and-convex assumption cannot catch each other's mistakes.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "three blobs",
                    listOf(
                        FigurePoint(0f, 0.542f), FigurePoint(0.25f, 0.222f), FigurePoint(0.5f, 0.371f),
                        FigurePoint(0.75f, 0.521f), FigurePoint(1f, 0.606f),
                    ),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "two rings",
                    listOf(
                        FigurePoint(0f, 0.904f), FigurePoint(0.25f, 0.756f), FigurePoint(0.5f, 0.761f),
                        FigurePoint(0.75f, 0.646f), FigurePoint(1f, 0.545f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            markers = listOf(
                FigurePoint(0.25f, 0.222f, "k = 3, correct"),
                FigurePoint(1f, 0.545f, "k = 6, wrong", FigureTone.Warn),
                FigurePoint(0f, 0.904f, "true k = 2", FigureTone.Muted),
            ),
            xLabel = "k, 2 → 6",
            yLabel = "Davies-Bouldin, 0 to 1.2 (lower is better)",
        ),
    ),
    whatIsIt = listOf(
        "Davies-Bouldin is a mean over clusters of the worst ratio (spreadᵢ + spreadⱼ) / distance(centroidᵢ, centroidⱼ), for each cluster paired against its most similar neighbor. Lower is better here — the opposite convention from silhouette — because it's measuring how much clusters overlap in scale relative to how far apart they sit, and 0 is the (unreachable) ideal of infinitely tight, infinitely separated clusters.",
        "On the lab's three well-separated blobs, DB correctly bottoms out at k = 3 (0.267, the lowest across k = 2..6) — the same answer silhouette gives. On two concentric rings, DB's minimum is at k = 6 (0.654), again matching silhouette's wrong answer exactly rather than the true k = 2.",
        "Two indices computed from completely different formulas — one from per-point margins, one from cluster-level compactness-versus-separation ratios — agree on both the right call and the wrong one. That's not a coincidence: both assume clusters are roughly compact and convex, so both misjudge the same non-convex shape the same way. Agreement between two metrics is reassuring only when they can fail independently; here, they can't.",
    ),
    steps = listOf(
        StepCard(1, "Cluster the Data", "Run k-means for a candidate k.", 0xFF0EA5E9),
        StepCard(2, "Compute Each Cluster's Spread", "Mean distance from members to their own centroid.", 0xFF3B82F6),
        StepCard(3, "Pair Each Cluster with Its Worst Match", "Max of (spreadᵢ+spreadⱼ)/distance over all j≠i.", 0xFF8B5CF6),
        StepCard(4, "Average Over Clusters", "Lower is better — 0 is the unreachable ideal.", 0xFFF59E0B),
        StepCard(5, "Sweep k on Blobs", "Minimum at k=3 (0.267) — the true structure.", 0xFFEC4899),
        StepCard(6, "Sweep k on Rings", "Minimum at k=6 (0.654) — the same wrong answer as silhouette.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Davies-Bouldin", "mean over i of max_j (spreadᵢ+spreadⱼ)/dist(i,j)", "Lower is better."),
        FormulaEntry("Blobs, k=3", "0.267", "The minimum across k=2..6 — correctly finds the true structure."),
        FormulaEntry("Rings, k=2", "1.085", "The true k — far from the minimum."),
        FormulaEntry("Rings, k=6", "0.654", "Where DB actually minimizes — same wrong k as silhouette."),
        FormulaEntry("Ideal", "0", "Unreachable — infinitely tight, infinitely separated clusters."),
        FormulaEntry("Direction", "lower is better", "Opposite convention from silhouette."),
    ),
    notationKey = listOf(
        NotationEntry("spread", "mean distance from cluster members to their own centroid"),
        NotationEntry("separation", "distance between two cluster centroids"),
        NotationEntry("worst pairing", "for each cluster, the neighbor it's least distinguishable from"),
        NotationEntry("lower is better", "the opposite convention from silhouette — mind the direction when comparing"),
        NotationEntry("compactness assumption", "same one silhouette makes — the reason the two indices fail together"),
        NotationEntry("silhouette", "the related index that agrees with DB on both the right and wrong answer — see that topic"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Agrees with silhouette when both are right",
            accentColor = 0xFF0EA5E9,
            code = """
                from sklearn.metrics import davies_bouldin_score
                from sklearn.cluster import KMeans

                for k in range(2, 7):
                    labels = KMeans(n_clusters=k, n_init=10).fit_predict(blobs)
                    print(k, davies_bouldin_score(blobs, labels))
                # 2 0.650
                # 3 0.267  <- minimum, matches the true 3 clusters (and silhouette's peak)
                # 4 0.445
                # 5 0.625
                # 6 0.727
            """.trimIndent(),
        ),
        CodeBlock(
            title = "And agrees with it when both are wrong",
            accentColor = 0xFFEC4899,
            code = """
                for k in range(2, 7):
                    labels = KMeans(n_clusters=k, n_init=10).fit_predict(rings)
                    print(k, davies_bouldin_score(rings, labels))
                # 2 1.085  <- the true structure, and the worst score of the five
                # 3 0.907
                # 4 0.913
                # 5 0.775
                # 6 0.654  <- minimum -- the same wrong k silhouette also picked
                #
                # Two indices, unrelated formulas, same failure -- because both assume clusters are
                # compact and convex, and a ring is neither. Agreement here is not independent
                # confirmation; it's the same blind spot measured twice.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF0EA5E9, "Choosing k", "A second opinion alongside silhouette, computed from cluster geometry."),
        ApplicationCard("chart", 0xFF3B82F6, "Automated Pipelines", "Cheaper to compute than silhouette on large datasets."),
        ApplicationCard("check", 0xFF8B5CF6, "Cluster Compactness", "Directly reads spread-versus-separation per cluster."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Non-convex shapes — it fails identically to silhouette on the same data."),
    ),
    takeaways = listOf(
        "Davies-Bouldin is a mean worst-case spread-to-separation ratio over clusters — lower is better.",
        "On three well-separated blobs, it correctly minimizes at k=3 (0.267).",
        "On two concentric rings, its minimum is k=6 (0.654), not the true k=2 (1.085).",
        "That's the same wrong answer silhouette gives on the identical rings dataset.",
        "Both indices assume compact, convex clusters, so both misjudge non-convex shapes the same way.",
        "Agreement between the two is not independent confirmation — it's a shared blind spot.",
        "Use both together on data expected to be convex; neither settles a genuinely non-convex case.",
    ),
    crossLinks = listOf(
        CrossLink("silhouette_score", "Silhouette Score"),
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
