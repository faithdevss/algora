package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val umapContent = TopicContent(
    topicId = "umap",
    whatIsIt = listOf(
        "UMAP builds a weighted k-nearest-neighbour graph over the data and then lays that graph out in two dimensions with attraction along its edges and repulsion everywhere else. The published derivation is in the language of Riemannian geometry and fuzzy simplicial sets; the algorithm that falls out of it is a graph construction followed by a force-directed layout, and it is worth holding both descriptions at once — the theory explains the choices, and the implementation is what runs.",
        "Two details in the graph construction carry most of the practical difference from t-SNE. Each point's distance to its *nearest* neighbour, ρᵢ, is subtracted before the exponential, so every point is joined to something with weight 1 and no point can be stranded by a change in local density. And the two directed edge weights are combined as a + b − ab — a union rather than an average — so an edge survives if either endpoint counts the other as a neighbour. Those two choices are what keep the graph connected across regions of very different density, which is the situation that makes t-SNE's per-point σ do all the work.",
        "In practice UMAP is much faster (approximate nearest neighbours plus negative sampling, rather than an O(n²) affinity matrix), and it tends to keep more of the global arrangement — which this topic's simulation measures rather than asserts, on the same three uneven clusters t-SNE is run on, along with what it does *not* keep. A good deal of that global advantage comes from the initialisation being spectral rather than random, which is a fair thing to attribute since it is part of the method as shipped. The same caution applies as ever: the layout is optimised for a neighbourhood objective, so cluster size and the exact gaps between clusters are still not quantities to read off the picture.",
    ),
    steps = listOf(
        StepCard(1, "Find k Nearest Neighbours", "Approximately, via NN-Descent — this is where the speed advantage starts.", 0xFFEC4899),
        StepCard(2, "Set ρᵢ and Solve σᵢ", "ρᵢ is the nearest-neighbour distance; σᵢ is solved so the weights sum to log₂k.", 0xFFF472B6),
        StepCard(3, "Symmetrise as a Union", "wᵢⱼ = a + b − ab. An edge survives if either endpoint claims it.", 0xFF8B5CF6),
        StepCard(4, "Initialise Spectrally", "The Laplacian eigenmap of the graph — informed, not random, and a large part of why global structure survives.", 0xFF6366F1),
        StepCard(5, "Optimise the Layout", "Attraction along edges, repulsion by negative sampling, on a cross-entropy objective.", 0xFF10B981),
        StepCard(6, "Set n_neighbors and min_dist", "Local versus global emphasis, and how tightly points may pack. Both change the picture substantially.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("Local connectivity", "ρᵢ = min_{j∈N(i)} d(xᵢ, xⱼ)", "Guarantees every point has one edge at full weight."),
        FormulaEntry("Directed weight", "wᵢ|ⱼ = exp(−max(0, d(xᵢ,xⱼ) − ρᵢ)/σᵢ)", "σᵢ solved so Σⱼ wᵢ|ⱼ = log₂k."),
        FormulaEntry("Symmetrisation", "wᵢⱼ = wᵢ|ⱼ + wⱼ|ᵢ − wᵢ|ⱼwⱼ|ᵢ", "Probabilistic t-conorm — a union, not a mean."),
        FormulaEntry("Layout kernel", "Ψ(y) = (1 + a‖y‖^{2b})⁻¹", "a and b fitted to the min_dist curve; a ≈ 1.577, b ≈ 0.895 at min_dist = 0.1."),
        FormulaEntry("Objective", "Σ wᵢⱼ log(wᵢⱼ/vᵢⱼ) + (1−wᵢⱼ) log((1−wᵢⱼ)/(1−vᵢⱼ))", "Cross-entropy — the repulsive second term is what t-SNE's KL lacks."),
        FormulaEntry("Cost", "≈ O(n^1.14) empirically", "Approximate k-NN plus negative sampling; t-SNE's exact form is O(n²)."),
    ),
    notationKey = listOf(
        NotationEntry("n_neighbors", "k — small emphasises local detail, large emphasises global shape"),
        NotationEntry("min_dist", "how tightly points may pack in the layout; purely aesthetic, entirely visible"),
        NotationEntry("ρᵢ", "distance to the nearest neighbour, subtracted for local connectivity"),
        NotationEntry("fuzzy simplicial set", "the paper's name for the weighted neighbourhood graph"),
        NotationEntry("negative sampling", "approximating the repulsive term with a handful of random pairs"),
        NotationEntry("spectral initialisation", "Laplacian eigenmap starting layout"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The two parameters that matter, and what each one does",
            accentColor = 0xFFEC4899,
            code = """
                import umap
                from sklearn.datasets import load_digits

                X, y = load_digits(return_X_y=True)

                # n_neighbors is the local/global dial. Small: fine structure, fragmented view.
                # Large: coarse structure, clusters merge. It is not a quality setting.
                local  = umap.UMAP(n_neighbors=5,  min_dist=0.1, random_state=0).fit_transform(X)
                global_ = umap.UMAP(n_neighbors=50, min_dist=0.1, random_state=0).fit_transform(X)

                # min_dist only affects packing in the layout — it changes how the plot looks,
                # not what the graph said. Use 0.0 before clustering the embedding, 0.1+ to look at.
                tight = umap.UMAP(n_neighbors=15, min_dist=0.0, random_state=0).fit_transform(X)

                # Unlike t-SNE, UMAP has a transform() for unseen points.
                mapper = umap.UMAP(random_state=0).fit(X[:1000])
                held_out = mapper.transform(X[1000:])
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Scoring it against t-SNE instead of eyeballing both",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np
                import umap
                from sklearn.manifold import TSNE
                from sklearn.neighbors import NearestNeighbors

                def preservation(A, B, k=10):
                    na = NearestNeighbors(n_neighbors=k + 1).fit(A).kneighbors(A)[1][:, 1:]
                    nb = NearestNeighbors(n_neighbors=k + 1).fit(B).kneighbors(B)[1][:, 1:]
                    return np.mean([len(set(a) & set(b)) / k for a, b in zip(na, nb)])

                def separation(P, lab):
                    d = np.linalg.norm(P[:, None] - P[None], axis=2)
                    same = lab[:, None] == lab[None]
                    np.fill_diagonal(same, False)
                    return d[~same].mean() / d[same].mean()

                Zu = umap.UMAP(random_state=0).fit_transform(X)
                Zt = TSNE(init="pca", random_state=0).fit_transform(X)

                print(round(preservation(X, Zu), 3), round(preservation(X, Zt), 3))
                print(round(separation(Zu, y), 2), round(separation(Zt, y), 2), round(separation(X, y), 2))
                # Report both numbers. "UMAP preserves global structure better" is a claim about
                # a dataset, and it is cheap to check on yours.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFFEC4899, "Single-Cell Atlases", "It replaced t-SNE as the default in most scRNA-seq pipelines, largely because it scales to millions of cells."),
        ApplicationCard("search", 0xFF8B5CF6, "Embedding Store Inspection", "Looking at what a vector database actually contains before trusting its retrieval."),
        ApplicationCard("chip", 0xFF6366F1, "Reduction Before Clustering", "With min_dist = 0, the layout is used as a preprocessing step for HDBSCAN rather than as a picture."),
    ),
    takeaways = listOf(
        "It is a weighted k-NN graph plus a force-directed layout; the topology in the paper motivates the weights.",
        "Local connectivity (subtracting ρᵢ) plus union symmetrisation is what survives a density change.",
        "Faster than t-SNE by approximate neighbours and negative sampling, and it has a transform() for new points.",
        "It usually keeps more global structure — measure that on your data rather than assuming it.",
        "n_neighbors is a local/global dial, not a quality setting; min_dist only affects packing.",
    ),
    crossLinks = listOf(
        CrossLink("tsne", "t-SNE"),
        CrossLink("hdbscan", "HDBSCAN"),
        CrossLink("spectral_clustering", "Spectral Clustering"),
        CrossLink("graph", "Graph (DSA)"),
    ),
)
