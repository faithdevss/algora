package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val hierarchicalDivisiveContent = TopicContent(
    topicId = "hierarchical_divisive",
    whatIsIt = listOf(
        "Divisive clustering builds the same dendrogram as agglomerative clustering, from the opposite end. It starts with every point in one cluster and repeatedly splits the least cohesive one, rather than starting with singletons and merging the closest pair.",
        "The direction changes where the consequential decisions happen. Divisive makes its most important split first, with the entire dataset in view, so the top-level structure is decided by a globally informed choice — which is why it tends to produce better high-level clusters when you only want two or three. Agglomerative decides at the bottom, where each merge is between two small groups and the algorithm has no idea what the overall structure will turn out to be. In both cases the decision is permanent: neither ever revisits a split or a merge.",
        "Cost is what keeps divisive rare. Finding the genuinely best split of a cluster of n points means checking 2^(n−1) − 1 possible partitions, so the exact algorithm is intractable beyond trivial sizes. Practical implementations approximate — DIANA splits off the point with the highest average dissimilarity and grows a splinter group around it, while a common shortcut is simply to run 2-means on the chosen cluster, which is what the simulation on this page does. That approximation makes divisive tractable and also gives up the exactness that was its theoretical appeal, which is a fair summary of why agglomerative is the default in every standard library.",
    ),
    steps = listOf(
        StepCard(1, "Start With One Cluster", "Everything together — the root of the dendrogram.", 0xFF3B82F6),
        StepCard(2, "Find the Least Cohesive", "Largest diameter, or highest average within-cluster dissimilarity.", 0xFF818CF8),
        StepCard(3, "Split It in Two", "Exactly is intractable, so approximate — DIANA's splinter group, or 2-means.", 0xFF60A5FA),
        StepCard(4, "Record the Split Height", "The cluster's diameter becomes that node's height in the dendrogram.", 0xFF10B981),
        StepCard(5, "Repeat", "Until singletons, or until you have the number of clusters you wanted.", 0xFF14B8A6),
        StepCard(6, "Cut the Tree", "The dendrogram is the output; a horizontal cut turns it into a labelling.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Exact split cost", "2^(n−1) − 1 partitions", "Why the exact version is not used."),
        FormulaEntry("Cluster diameter", "max d(xᵢ, xⱼ) within the cluster", "The usual cohesion measure."),
        FormulaEntry("DIANA splinter", "start from the most dissimilar point, grow", "The classical approximation."),
        FormulaEntry("Practical cost", "O(n²) with a 2-means split", "Comparable to agglomerative."),
        FormulaEntry("Agglomerative cost", "O(n³) naive, O(n² log n) with a heap", "For contrast."),
        FormulaEntry("Output", "a dendrogram, not a labelling", "Both directions produce a tree."),
    ),
    notationKey = listOf(
        NotationEntry("divisive", "top-down; split from one cluster"),
        NotationEntry("agglomerative", "bottom-up; merge from singletons"),
        NotationEntry("DIANA", "DIvisive ANAlysis, the classical algorithm"),
        NotationEntry("dendrogram", "the tree of nested clusterings"),
        NotationEntry("cophenetic distance", "the dendrogram height at which two points first join"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Divisive by recursive bisection",
            accentColor = 0xFF3B82F6,
            code = """
                import numpy as np
                from sklearn.cluster import KMeans
                from scipy.spatial.distance import pdist

                def divisive(X, k):
                    clusters = [np.arange(len(X))]
                    while len(clusters) < k:
                        # Split whichever cluster is least cohesive. Diameter is the usual
                        # choice; average within-cluster dissimilarity is the DIANA one.
                        # Track clusters by index: list.remove() on NumPy arrays compares them
                        # element-wise and raises "truth value ... is ambiguous".
                        i = max(
                            (j for j, c in enumerate(clusters) if len(c) > 1),
                            key=lambda j: pdist(X[clusters[j]]).max(),
                            default=None,
                        )
                        if i is None:
                            break
                        target = clusters.pop(i)
                        labels = KMeans(n_clusters=2, n_init=10).fit_predict(X[target])
                        clusters += [target[labels == 0], target[labels == 1]]
                    return clusters

                # Note what this gives up: the exact best split is intractable, so "least
                # cohesive, bisected by 2-means" is a heuristic on top of a heuristic.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Agglomerative, for comparison",
            accentColor = 0xFF10B981,
            code = """
                from scipy.cluster.hierarchy import linkage, dendrogram, fcluster

                # SciPy only ships the agglomerative direction, which is itself a comment on
                # how the two are used in practice.
                Z = linkage(X, method="ward")      # ward minimizes within-cluster variance
                labels = fcluster(Z, t=3, criterion="maxclust")

                # The linkage choice matters more than the direction does:
                #   single   -> chains; finds elongated clusters, and chains through noise
                #   complete -> compact, roughly equal-diameter clusters
                #   average  -> between the two
                #   ward     -> variance-minimizing; the usual default for Euclidean data
                dendrogram(Z, truncate_mode="lastp", p=12)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("book", 0xFF3B82F6, "Taxonomy Construction", "Document and species hierarchies, where the top-level division is the one that has to be right."),
        ApplicationCard("chart", 0xFF818CF8, "Few Large Clusters", "Wanting two or three groups makes divisive cheap — it stops after two or three splits."),
        ApplicationCard("flask", 0xFF10B981, "Phylogenetics", "Both directions are used, and the resulting trees are compared for agreement."),
    ),
    takeaways = listOf(
        "Same dendrogram as agglomerative, built downward by splitting rather than upward by merging.",
        "Its most consequential decision is made first, with the whole dataset in view.",
        "The exact best split is 2^(n−1) − 1 partitions, so every practical implementation approximates.",
        "Neither direction revisits a decision — a wrong early split or merge is permanent.",
    ),
    crossLinks = listOf(
        CrossLink("hierarchical_clustering", "Hierarchical (Agglomerative)"),
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("tree", "Tree (DSA)"),
        CrossLink("birch", "BIRCH"),
    ),
)
