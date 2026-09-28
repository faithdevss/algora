package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val kernelPcaContent = TopicContent(
    topicId = "kernel_pca",
    whatIsIt = listOf(
        "PCA searches for the directions of greatest variance, and \"direction\" means a straight axis. When the structure in the data is not linear — two concentric rings, a spiral, a curved sheet — the answer PCA is looking for does not exist in the space it searches, and no amount of iteration finds it. Kernel PCA keeps every step of PCA and changes only the inner product.",
        "The mechanism is the kernel trick, the same one behind the RBF support vector machine. PCA can be written entirely in terms of inner products between data points; replace every ⟨xᵢ, xⱼ⟩ with k(xᵢ, xⱼ) = exp(−γ‖xᵢ−xⱼ‖²) and you are doing PCA in the feature space that kernel implicitly defines — a space that for the RBF kernel is infinite-dimensional, and which nobody ever constructs. You eigendecompose the n×n Gram matrix instead of the d×d covariance matrix, and the components come out as coefficients over the training points rather than as vectors in feature space.",
        "Two costs come with it, and both are structural. The matrix is n×n, so memory grows with the number of samples rather than the number of features — the exact opposite of PCA's scaling, and the reason kernel PCA is a small-data method. And there is no simple way back: because the feature map is never written down, projecting a point into the reduced space is easy but reconstructing a data point from its reduced coordinates is a separate optimisation problem (the pre-image problem) with no closed-form solution. Kernel PCA is for finding structure, not for compressing and restoring.",
    ),
    steps = listOf(
        StepCard(1, "Choose the Kernel", "RBF, polynomial, sigmoid. This is the modelling decision — it fixes what 'similar' means before anything is fitted.", 0xFFEC4899),
        StepCard(2, "Build the Gram Matrix", "Kᵢⱼ = k(xᵢ, xⱼ), n×n. Every pair, which is where the memory goes.", 0xFFF472B6),
        StepCard(3, "Double-Centre It", "K̃ = K − 1ₙK − K1ₙ + 1ₙK1ₙ. The feature-space mean is not at the origin and this is the only way to move it there.", 0xFF8B5CF6),
        StepCard(4, "Eigendecompose K̃", "Top eigenvectors αᵏ, eigenvalues λₖ. Normalise so that λₖ‖αᵏ‖² = 1.", 0xFF6366F1),
        StepCard(5, "Project", "Point i's k-th component is √λₖ·αᵏᵢ. New points project through their kernel row against the training set.", 0xFF10B981),
        StepCard(6, "Tune γ Against the Task", "Too wide and everything is similar; too narrow and every point is its own island. Neither failure announces itself.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("RBF kernel", "k(x, y) = exp(−γ‖x−y‖²)", "γ = 1/(2σ²); the length scale is 1/√(2γ)."),
        FormulaEntry("Polynomial kernel", "k(x, y) = (⟨x, y⟩ + c)ᵈ", "A finite-dimensional feature space, unlike the RBF."),
        FormulaEntry("Centring", "K̃ = K − 1ₙK − K1ₙ + 1ₙK1ₙ", "1ₙ is the n×n matrix of 1/n. Omit it and the components describe the offset, not the spread."),
        FormulaEntry("Eigenproblem", "K̃αᵏ = nλₖαᵏ", "n×n, not d×d — the cost swaps from features to samples."),
        FormulaEntry("Normalisation", "λₖ‖αᵏ‖² = 1", "Makes the feature-space component a unit vector."),
        FormulaEntry("Projection", "zₖ(x) = Σᵢ αᵏᵢ k(xᵢ, x)", "A new point needs its kernel against every training point."),
        FormulaEntry("Cost", "O(n³) to decompose, O(n²) to store", "The reason this stays small-data."),
    ),
    notationKey = listOf(
        NotationEntry("φ(x)", "the feature map — implied by the kernel, never computed"),
        NotationEntry("K", "Gram matrix of pairwise kernel values"),
        NotationEntry("K̃", "the double-centred Gram matrix"),
        NotationEntry("γ", "RBF bandwidth — the parameter that decides the answer"),
        NotationEntry("αᵏ", "eigenvector of K̃; coefficients over the training points"),
        NotationEntry("pre-image problem", "recovering a data point from its feature-space representation — not solvable in closed form"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The case PCA cannot do",
            accentColor = 0xFFEC4899,
            code = """
                from sklearn.datasets import make_circles
                from sklearn.decomposition import PCA, KernelPCA
                from sklearn.linear_model import LogisticRegression
                from sklearn.model_selection import cross_val_score

                X, y = make_circles(n_samples=400, factor=0.35, noise=0.05, random_state=0)

                pca = PCA(n_components=2).fit_transform(X)
                kpca = KernelPCA(n_components=2, kernel="rbf", gamma=4.0).fit_transform(X)

                # Score each embedding by how well a LINEAR model does on top of it — the whole
                # question is whether the representation made the problem linear.
                print(cross_val_score(LogisticRegression(), pca, y, cv=5).mean())    # ~0.50
                print(cross_val_score(LogisticRegression(), kpca, y, cv=5).mean())   # ~1.00
            """.trimIndent(),
        ),
        CodeBlock(
            title = "By hand, so the centring step is visible",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                def kernel_pca(X, gamma, n_components=2):
                    d2 = ((X[:, None, :] - X[None]) ** 2).sum(axis=2)
                    K = np.exp(-gamma * d2)

                    # The step that is easy to skip and silently wrong to skip: the mean of the
                    # data in feature space is not the origin, and K knows nothing about it.
                    n = len(K)
                    one = np.ones((n, n)) / n
                    Kc = K - one @ K - K @ one + one @ K @ one

                    vals, vecs = np.linalg.eigh(Kc)          # ascending
                    vals, vecs = vals[::-1], vecs[:, ::-1]
                    return vecs[:, :n_components] * np.sqrt(np.maximum(vals[:n_components], 0))

                # gamma is not a detail. Sweep it and score the separation before trusting a picture:
                for g in (0.5, 1, 2, 4, 8, 16):
                    Z = kernel_pca(X, g)
                    print(g, round(float(np.corrcoef(Z[:, 0], y)[0, 1] ** 2), 3))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFEC4899, "Non-Linear Denoising", "Project onto the leading kernel components and back to remove structure the linear subspace cannot express — the classic demonstration on noisy handwritten digits."),
        ApplicationCard("flask", 0xFF8B5CF6, "Novelty Detection", "Reconstruction error in feature space flags points that do not lie on the manifold the training data traced."),
        ApplicationCard("chip", 0xFF6366F1, "Process Monitoring", "Chemical and manufacturing plants have curved operating envelopes; kernel PCA control charts catch excursions a linear model calls normal."),
    ),
    takeaways = listOf(
        "It is PCA with every inner product replaced by a kernel — same algorithm, different geometry.",
        "You eigendecompose an n×n Gram matrix, so cost scales with samples rather than features.",
        "Double-centring the Gram matrix is mandatory; skipping it changes what the components mean.",
        "γ decides the answer: too wide and everything is similar, too narrow and every point is isolated.",
        "There is no cheap inverse — the pre-image problem has no closed-form solution, so this is a structure-finding tool, not a codec.",
    ),
    crossLinks = listOf(
        CrossLink("pca", "Principal Component Analysis (PCA)"),
        CrossLink("svm_rbf", "SVM (Radial Basis Function)"),
        CrossLink("spectral_clustering", "Spectral Clustering"),
        CrossLink("lle", "Locally Linear Embedding (LLE)"),
    ),
)
