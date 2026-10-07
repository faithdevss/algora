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

internal val spectralClusteringContent = TopicContent(
    topicId = "spectral_clustering",
    figure = Figure(
        caption = "The Laplacian spectrum of a 4-nearest-neighbour graph over 60 two-moons points, " +
            "first six eigenvalues, axis top 0.254. Two of them are exactly zero, and that is the " +
            "diagnostic: the multiplicity of the zero eigenvalue equals the number of connected " +
            "components, so the graph itself has already answered how many clusters there are before " +
            "any clustering runs. λ₃ = 0.060 and λ₄ = 0.064 sit low, then the spectrum jumps to " +
            "0.239 — a gap you can read k from. Nothing about the moons' shape appears anywhere in " +
            "this; connectivity replaced geometry two steps ago.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "λᵢ",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0.2f, 0f), FigurePoint(0.4f, 0.237f),
                        FigurePoint(0.6f, 0.252f), FigurePoint(0.8f, 0.943f),
                        FigurePoint(1f, 1f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.2f, 0f, "λ₁ = λ₂ = 0"),
                FigurePoint(0.8f, 0.943f, "the gap", FigureTone.Warn),
            ),
            xLabel = "i  (1 → 6)",
            yLabel = "λ  (0 → 0.254)",
        ),
    ),
    whatIsIt = listOf(
        "Spectral clustering changes the question from \"which points are close together\" to \"which points are connected\". Build a similarity graph over the data, then partition that graph by cutting as few strong edges as possible — and the clusters that fall out need not be compact, convex or anything else.",
        "Two interleaved crescents make the difference concrete. Each crescent's own centroid sits closer to parts of the other crescent than to its own tips, so k-means cannot possibly separate them: its boundaries are perpendicular bisectors between centres, and no such line splits the moons correctly. But in the similarity graph the two crescents are two components joined by almost nothing, and cutting them apart is easy.",
        "The mechanism is a relaxation. Minimum normalized cut is NP-hard exactly, but allowing the cluster indicator to take real values instead of ±1 turns it into an eigenvalue problem — and the second-smallest eigenvector of the graph Laplacian, the Fiedler vector, is the relaxed solution. Its sign gives a two-way cut; for k clusters you take the first k eigenvectors and run k-means in that embedding, which is why spectral clustering is often described as k-means applied somewhere better. The costs are unavoidable: an n×n affinity matrix and an eigendecomposition on top, so it is a small-data method, and the affinity scale σ matters as much as any parameter in this whole family.",
    ),
    steps = listOf(
        StepCard(1, "Build the Affinity Matrix", "RBF similarity, or a k-nearest-neighbour graph. σ or k is the real parameter.", 0xFF3B82F6),
        StepCard(2, "Form the Laplacian", "L = D − W, or a normalized variant. D is the diagonal degree matrix.", 0xFF818CF8),
        StepCard(3, "Take the Smallest Eigenvectors", "The trivial constant one is discarded; the next is the Fiedler vector.", 0xFF60A5FA),
        StepCard(4, "Embed the Points", "Each point's coordinates are its entries in the first k eigenvectors.", 0xFF10B981),
        StepCard(5, "Cluster the Embedding", "k-means in that space — where the clusters *are* now compact.", 0xFF14B8A6),
        StepCard(6, "Check σ", "Too small disconnects the graph, too large connects everything. It decides the answer.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("RBF affinity", "Wᵢⱼ = exp(−‖xᵢ−xⱼ‖²/2σ²)", "Similarity, not distance."),
        FormulaEntry("Unnormalized Laplacian", "L = D − W", "D is the diagonal of row sums."),
        FormulaEntry("Normalized (Ng-Jordan-Weiss)", "L_sym = I − D^(−½)WD^(−½)", "Usually the better choice."),
        FormulaEntry("Fiedler vector", "the eigenvector of λ₂", "λ₁ = 0 is the trivial constant vector."),
        FormulaEntry("Algebraic connectivity", "λ₂", "Zero exactly when the graph is disconnected."),
        FormulaEntry("Complexity", "O(n³) for a dense eigendecomposition", "The reason it does not scale."),
    ),
    notationKey = listOf(
        NotationEntry("W", "affinity (similarity) matrix"),
        NotationEntry("D", "degree matrix — diagonal of W's row sums"),
        NotationEntry("Fiedler vector", "eigenvector of the second-smallest eigenvalue"),
        NotationEntry("normalized cut", "the objective; NP-hard exactly, relaxed here"),
        NotationEntry("σ", "affinity bandwidth — the parameter that matters most"),
        NotationEntry("spectral embedding", "the coordinates the eigenvectors define"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The case k-means cannot do",
            accentColor = 0xFF3B82F6,
            code = """
                from sklearn.datasets import make_moons
                from sklearn.cluster import KMeans, SpectralClustering
                from sklearn.metrics import adjusted_rand_score

                X, y = make_moons(n_samples=400, noise=0.06, random_state=0)

                km = KMeans(n_clusters=2, n_init=10).fit_predict(X)
                sc = SpectralClustering(
                    n_clusters=2,
                    affinity="nearest_neighbors",   # more robust than picking a gamma
                    n_neighbors=10,
                    assign_labels="kmeans",
                ).fit_predict(X)

                print(round(adjusted_rand_score(y, km), 3))   # ~0.2 — the split cuts both moons
                print(round(adjusted_rand_score(y, sc), 3))   # ~1.0 — exact
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Building it by hand, to see where the clusters come from",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np
                from scipy.linalg import eigh
                from sklearn.cluster import KMeans

                def laplacian(X, sigma=0.1):
                    d2 = ((X[:, None, :] - X[None]) ** 2).sum(axis=2)
                    W = np.exp(-d2 / (2 * sigma ** 2))
                    np.fill_diagonal(W, 0)
                    return np.diag(W.sum(axis=1)) - W        # unnormalized Laplacian

                def spectral(X, k, sigma=0.1):
                    vals, vecs = eigh(laplacian(X, sigma))

                    # The FIRST k eigenvectors span the cluster indicators (the constant one
                    # belonging to vals[0] ~ 0 is one of them): the standard recipe.
                    embedding = vecs[:, :k]
                    return KMeans(n_clusters=k, n_init=10).fit_predict(embedding)

                # The number of eigenvalues at (near) zero equals the number of connected
                # components, which is a genuinely useful diagnostic for choosing k:
                vals = eigh(laplacian(X), eigvals_only=True)
                print(np.round(vals[:6], 5))    # look for the gap
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF3B82F6, "Image Segmentation", "Normalized cuts on a pixel similarity graph — the application the method was developed for."),
        ApplicationCard("share", 0xFF818CF8, "Community Detection", "Social and citation graphs, where the affinity matrix already exists and no coordinates do."),
        ApplicationCard("chip", 0xFF10B981, "Circuit Partitioning", "Splitting a netlist across chips while cutting as few wires as possible."),
    ),
    takeaways = listOf(
        "It clusters by connectivity rather than compactness, so cluster shape is unconstrained.",
        "Minimum normalized cut is NP-hard; relaxing the indicator to real values makes it an eigenvalue problem.",
        "The first k eigenvectors give an embedding in which the clusters *are* compact, so k-means finishes the job.",
        "O(n²) memory and O(n³) eigendecomposition make it small-data, and σ decides the answer.",
    ),
    crossLinks = listOf(
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("pca", "PCA"),
        CrossLink("graph", "Graph (DSA)"),
        CrossLink("affinity_propagation", "Affinity Propagation"),
    ),
)
