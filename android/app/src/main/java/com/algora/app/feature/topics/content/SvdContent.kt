package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureLayer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val svdContent = TopicContent(
    topicId = "svd",
    figure = Figure(
        caption = "Read as a map, every matrix — square or not, invertible or not, full rank or " +
            "not — is these three steps and nothing else: rotate (or reflect), stretch along the axes, rotate (or reflect) " +
            "again. The factorization exists unconditionally, which is why so many methods turn " +
            "out to be it in disguise: PCA is the SVD of a centred data matrix, least squares " +
            "through the pseudo-inverse is the SVD with zero singular values dropped, LSA is the " +
            "SVD of a term-document matrix, and the condition number is σ₁/σₙ, read straight off " +
            "the middle block. Truncating that middle block to its k largest entries gives the " +
            "best rank-k approximation that exists in Frobenius norm (Eckart–Young), and the error " +
            "left over is exactly the norm of the singular values thrown away.",
        shape = FigureShape.LayerStack(
            layers = listOf(
                FigureLayer("Vᵀ", "rotate", FigureTone.Muted),
                FigureLayer("Σ", "stretch: σ₁ ≥ … ≥ σₙ ≥ 0", FigureTone.Accent),
                FigureLayer("U", "rotate", FigureTone.Muted),
            ),
            horizontal = true,
        ),
    ),
    whatIsIt = listOf(
        "Every matrix — square or not, invertible or not, full rank or not — factors as X = UΣVᵀ. The right singular vectors V are an orthonormal basis for the input space, the left singular vectors U are one for the output space, and Σ is diagonal and non-negative. Read as a map, it says any linear transformation is a rotation (or reflection), then an axis-aligned stretch, then another rotation (or reflection). There is nothing else it can be.",
        "That unconditional existence is why the SVD sits underneath so much else. PCA is the SVD of a centred data matrix. Least squares through the pseudo-inverse is the SVD with the zero singular values dropped. Latent semantic analysis is the SVD of a term-document matrix, and the matrix-completion recommenders of the 2000s are its truncated form fitted with missing entries. The condition number of a matrix is σ₁/σₙ. Learn the factorization once and a dozen apparently separate techniques become the same technique.",
        "The reason it is the tool for dimensionality reduction specifically is the Eckart–Young theorem: keep the k largest singular values and zero the rest, and you have the best rank-k approximation of the matrix in Frobenius norm — the best that exists, not merely the best anyone has found. The error you are left with is exactly the norm of the singular values you discarded, which turns \"how much can I compress this?\" into a number you can read off the spectrum before compressing anything.",
    ),
    steps = listOf(
        StepCard(1, "Start With the Matrix", "n rows of observations by d columns of features. No covariance, no Gram matrix — the factorization works on X directly.", 0xFFEC4899),
        StepCard(2, "Centre, If You Want PCA", "Subtract the column means. Skip this and v₁ chases the direction of the mean instead of the direction of spread.", 0xFFF472B6),
        StepCard(3, "Factor as UΣVᵀ", "V's columns are the axes in feature space, U's are the coordinates, Σ holds the scale of each.", 0xFF8B5CF6),
        StepCard(4, "Read the Spectrum", "σ₁ ≥ σ₂ ≥ … ≥ 0. The drop-off tells you the effective rank before you commit to a k.", 0xFF6366F1),
        StepCard(5, "Truncate to Rank k", "Keep the first k triples. Eckart–Young makes this the optimal rank-k approximation, not a heuristic.", 0xFF10B981),
        StepCard(6, "Check the Residual", "‖X − X_k‖ꜰ² = Σᵢ₌ₖ₊₁ σᵢ². The error is decided by the spectrum, not by the algorithm.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("The factorization", "X = UΣVᵀ", "U (n×n) and V (d×d) orthogonal, Σ (n×d) diagonal."),
        FormulaEntry("Relation to the Gram matrices", "XᵀX = VΣᵀΣVᵀ,  XXᵀ = UΣΣᵀUᵀ", "V holds the eigenvectors of XᵀX; σᵢ² are its eigenvalues."),
        FormulaEntry("PCA from the SVD", "λᵢ = σᵢ²/n on centred X", "Same components, no covariance matrix ever formed."),
        FormulaEntry("Truncation", "X_k = Σᵢ₌₁ᵏ σᵢuᵢvᵢᵀ", "A sum of k rank-1 outer products."),
        FormulaEntry("Eckart–Young", "min rank(B)≤k ‖X − B‖ꜰ = ‖X − X_k‖ꜰ", "Truncating is optimal — the theorem, not a bound."),
        FormulaEntry("Residual", "‖X − X_k‖ꜰ² = Σᵢ>ₖ σᵢ²", "The discarded singular values *are* the error."),
        FormulaEntry("Pseudo-inverse", "X⁺ = VΣ⁺Uᵀ", "Σ⁺ inverts the non-zero σ and leaves the zeros alone."),
        FormulaEntry("Cost", "O(nd·min(n,d))", "Randomized and truncated variants do far better for small k."),
    ),
    notationKey = listOf(
        NotationEntry("U", "left singular vectors — an orthonormal basis for the column space"),
        NotationEntry("Σ", "diagonal matrix of singular values, non-negative and descending"),
        NotationEntry("V", "right singular vectors — the axes in feature space"),
        NotationEntry("σᵢ", "the i-th singular value; σᵢ² is the energy along that axis"),
        NotationEntry("‖·‖ꜰ", "Frobenius norm — the square root of the sum of squared entries"),
        NotationEntry("rank-k", "expressible as a sum of k rank-1 outer products"),
        NotationEntry("X⁺", "Moore-Penrose pseudo-inverse, defined for any matrix at all"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The factorization, and checking the theorem rather than trusting it",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np

                rng = np.random.default_rng(0)
                X = rng.normal(size=(200, 8)) @ rng.normal(size=(8, 8))   # rank 8, correlated

                U, s, Vt = np.linalg.svd(X, full_matrices=False)
                print(np.round(s, 2))              # the spectrum: read the drop-off

                k = 3
                X_k = (U[:, :k] * s[:k]) @ Vt[:k]  # the rank-3 truncation

                # Eckart-Young says this error is exactly the norm of what we threw away.
                print(np.linalg.norm(X - X_k))     # ~54.80
                print(np.sqrt((s[k:] ** 2).sum())) # ~54.80 — identical

                # And no other rank-3 matrix does better. Sample some and check:
                for _ in range(3):
                    A = rng.normal(size=(200, k)) @ rng.normal(size=(k, 8))
                    assert np.linalg.norm(X - A) > np.linalg.norm(X - X_k)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "PCA is the SVD of centred data — and the centring is the whole difference",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.decomposition import PCA, TruncatedSVD
                import numpy as np

                X = np.random.default_rng(1).normal(size=(500, 6)) + 20.0   # far from the origin

                Xc = X - X.mean(axis=0)
                _, s, Vt = np.linalg.svd(Xc, full_matrices=False)

                pca = PCA(n_components=6).fit(X)
                print(np.allclose(np.abs(Vt), np.abs(pca.components_)))     # True
                print(np.allclose(s ** 2 / (len(X) - 1), pca.explained_variance_))  # True

                # TruncatedSVD does NOT centre — which is a feature, not an oversight: on a sparse
                # term-document matrix, centring would destroy the sparsity and blow up memory.
                # But on this data its first component points at the mean, not at the spread.
                svd = TruncatedSVD(n_components=1).fit(X)
                cos = np.abs(svd.components_[0] @ (X.mean(0) / np.linalg.norm(X.mean(0))))
                print(round(float(cos), 3))     # ~1.0 — it found where the data IS, not how it varies
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFEC4899, "Image Compression", "A greyscale image is a matrix; keeping the top singular values is the textbook rank-k demonstration and the reason the spectrum's decay rate matters."),
        ApplicationCard("finance", 0xFF8B5CF6, "Recommender Systems", "Latent-factor models factor the user-item matrix. The Netflix Prize era ran on truncated SVDs fitted over the observed entries only."),
        ApplicationCard("book", 0xFF6366F1, "Latent Semantic Analysis", "The SVD of a term-document matrix collapses synonyms onto shared components — an embedding, decades before the word was used that way."),
        ApplicationCard("chip", 0xFF10B981, "Numerical Stability", "The condition number σ₁/σₙ says how badly a linear solve will amplify error, and the pseudo-inverse gives a defined answer where the ordinary inverse does not exist."),
    ),
    takeaways = listOf(
        "Every matrix has an SVD — no squareness, invertibility or rank condition attached.",
        "PCA is the SVD of a centred data matrix; skip the centring and v₁ points at the mean instead of the spread.",
        "Eckart–Young: truncating the SVD is the *optimal* rank-k approximation in Frobenius norm.",
        "The residual is exactly the norm of the discarded singular values, so the spectrum prices compression in advance.",
        "The condition number and the pseudo-inverse both fall out of the same factorization.",
    ),
    crossLinks = listOf(
        CrossLink("pca", "Principal Component Analysis (PCA)"),
        CrossLink("incremental_pca", "Incremental PCA"),
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
    ),
)
