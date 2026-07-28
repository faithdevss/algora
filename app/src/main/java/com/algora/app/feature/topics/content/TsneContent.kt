package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val tsneContent = TopicContent(
    topicId = "tsne",
    whatIsIt = listOf(
        "t-SNE gives up on preserving distances and preserves neighbourhoods instead. It converts distances in the original space into probabilities — pⱼ|ᵢ is how likely point i would be to pick j as its neighbour — builds the same kind of distribution in the two-dimensional map, and then moves the map's points around until the two distributions agree, minimising KL(P‖Q) by gradient descent.",
        "Two design choices do the real work. The Gaussian width σᵢ is solved separately for each point by binary search, so that every neighbourhood has the same entropy — that is what perplexity controls, and it is why a dense cluster and a sparse one are treated even-handedly. And the map-space distribution uses a Student-t with one degree of freedom rather than a Gaussian. Its heavy tail lets moderately-distant points sit far apart in the map at little cost, which is the fix for the crowding problem: there is simply not enough room in two dimensions for everything that was mutually distant in two hundred.",
        "The result is the most-produced and most-over-read plot in machine learning. The asymmetric KL objective penalises putting nearby points far apart much more than the reverse, so local structure is what survives and global structure is what does not. In this topic's simulation, run on three clusters of deliberately unequal spread and separation, the two tight clusters come out *larger* in the map than the loose one, and the ratio between the far gap and the near gap collapses from about 6:1 to about 1:1 — both measured on screen rather than asserted. Cluster size in a t-SNE plot carries no information; distance between clusters carries very little; and the shape you get depends on the perplexity, the seed and the iteration count. It is a tool for noticing that groups exist, not for measuring them.",
    ),
    steps = listOf(
        StepCard(1, "Solve σᵢ per Point", "Binary search each point's Gaussian width so its neighbourhood entropy equals log(perplexity).", 0xFFEC4899),
        StepCard(2, "Build P", "Symmetrise the conditional probabilities into a joint distribution over pairs.", 0xFFF472B6),
        StepCard(3, "Initialise the Map", "Small random values, or a PCA initialisation — which is the cheapest way to keep some global structure.", 0xFF8B5CF6),
        StepCard(4, "Exaggerate Early", "Multiply P by ~4 for the first ~250 iterations so clusters separate before repulsion takes over.", 0xFF6366F1),
        StepCard(5, "Descend on KL(P‖Q)", "Q is a Student-t with one degree of freedom; its heavy tail is the crowding fix.", 0xFF10B981),
        StepCard(6, "Read It With Discipline", "Groups: yes. Cluster sizes, gaps, and anything about points that were far apart: no.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("Conditional affinity", "pⱼ|ᵢ = exp(−‖xᵢ−xⱼ‖²/2σᵢ²) / Σₖ≠ᵢ exp(−‖xᵢ−xₖ‖²/2σᵢ²)", "σᵢ is solved per point, not shared."),
        FormulaEntry("Perplexity", "Perp(Pᵢ) = 2^H(Pᵢ)", "An effective neighbour count; the usual range is 5–50."),
        FormulaEntry("Symmetrised P", "pᵢⱼ = (pⱼ|ᵢ + pᵢ|ⱼ)/2n", "Guarantees every point contributes to the loss."),
        FormulaEntry("Map affinity", "qᵢⱼ ∝ (1 + ‖yᵢ−yⱼ‖²)⁻¹", "Student-t, ν = 1 — the heavy tail solves crowding."),
        FormulaEntry("Objective", "KL(P‖Q) = Σᵢⱼ pᵢⱼ log(pᵢⱼ/qᵢⱼ)", "Asymmetric: splitting neighbours is punished far more than merging strangers."),
        FormulaEntry("Gradient", "∂C/∂yᵢ = 4Σⱼ(pᵢⱼ−qᵢⱼ)(yᵢ−yⱼ)(1+‖yᵢ−yⱼ‖²)⁻¹", "An attraction and a repulsion term."),
        FormulaEntry("Cost", "O(n²) exact, O(n log n) with Barnes-Hut", "Barnes-Hut is the default in practice above a few thousand points."),
    ),
    notationKey = listOf(
        NotationEntry("perplexity", "the one real parameter — roughly, how many neighbours each point considers"),
        NotationEntry("σᵢ", "per-point Gaussian bandwidth, found by binary search"),
        NotationEntry("Student-t", "the map-space distribution; ν = 1 gives the heavy tail"),
        NotationEntry("crowding problem", "there is not enough area in 2-D for all the pairwise distances of a high-D dataset"),
        NotationEntry("early exaggeration", "temporarily inflating P so clusters can separate"),
        NotationEntry("KL(P‖Q)", "the asymmetric divergence being minimised"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Running it, and checking what survived rather than trusting the picture",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                from sklearn.datasets import load_digits
                from sklearn.manifold import TSNE
                from sklearn.neighbors import NearestNeighbors

                X, y = load_digits(return_X_y=True)

                Z = TSNE(
                    n_components=2,
                    perplexity=30,
                    init="pca",          # keeps more global structure than random init
                    random_state=0,
                ).fit_transform(X)

                def neighbour_preservation(A, B, k=10):
                    na = NearestNeighbors(n_neighbors=k + 1).fit(A).kneighbors(A)[1][:, 1:]
                    nb = NearestNeighbors(n_neighbors=k + 1).fit(B).kneighbors(B)[1][:, 1:]
                    return np.mean([len(set(a) & set(b)) / k for a, b in zip(na, nb)])

                print(round(neighbour_preservation(X, Z), 3))   # what the map actually kept
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The three things a t-SNE plot does not mean",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np
                from sklearn.manifold import TSNE

                rng = np.random.default_rng(0)
                # Two tight clusters near each other, one loose cluster far away.
                X = np.vstack([
                    rng.normal([0, 0], 0.3, size=(150, 2)),
                    rng.normal([1.2, 0], 0.3, size=(150, 2)),
                    rng.normal([8, 8], 1.5, size=(150, 2)),
                ])
                lab = np.repeat([0, 1, 2], 150)

                Z = TSNE(perplexity=30, init="pca", random_state=0).fit_transform(X)

                def spread(P, c):
                    Q = P[lab == c]
                    return np.linalg.norm(Q - Q.mean(0), axis=1).mean()

                def gap(P, a, b):
                    return np.linalg.norm(P[lab == a].mean(0) - P[lab == b].mean(0))

                # 1. Cluster size is not preserved — the loose cluster does not stay the biggest.
                print([round(spread(X, c), 2) for c in (0, 1, 2)])
                print([round(spread(Z, c), 2) for c in (0, 1, 2)])

                # 2. Nor is the ratio of between-cluster distances.
                print(round(gap(X, 0, 2) / gap(X, 0, 1), 1), "->", round(gap(Z, 0, 2) / gap(Z, 0, 1), 1))

                # 3. And a different perplexity is a different picture, not a refinement of one.
                for p in (5, 30, 50):
                    _ = TSNE(perplexity=p, init="pca", random_state=0).fit_transform(X)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFFEC4899, "Single-Cell Genomics", "The standard first look at scRNA-seq data — thousands of cells, twenty thousand genes, and the question of how many cell types are present."),
        ApplicationCard("robot", 0xFF8B5CF6, "Inspecting Learned Embeddings", "Projecting a network's penultimate layer to see whether the classes it was trained on have actually separated."),
        ApplicationCard("Image", 0xFF6366F1, "Dataset Auditing", "Near-duplicates, label errors and unexpected sub-populations show up as clusters nobody put there."),
    ),
    takeaways = listOf(
        "It preserves neighbourhoods, not distances — that is the design, not a limitation to work around.",
        "Perplexity sets each point's own σ by binary search, which is what makes varying density tolerable.",
        "The Student-t in map space solves the crowding problem by making distance cheap.",
        "Cluster sizes and between-cluster gaps in the output are artefacts; do not measure them.",
        "Output depends on perplexity, seed and iteration count, so a single plot is one sample of many.",
    ),
    crossLinks = listOf(
        CrossLink("umap", "UMAP"),
        CrossLink("pca", "Principal Component Analysis (PCA)"),
        CrossLink("knn", "k-Nearest Neighbors"),
        CrossLink("lle", "Locally Linear Embedding (LLE)"),
    ),
)
